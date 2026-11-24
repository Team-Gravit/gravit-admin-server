package gravit.admin.adapter.gravit.infrastructure;

import gravit.admin.adapter.gravit.config.GravitProps;
import gravit.admin.adapter.gravit.dto.internal.GravitRequestDto;
import gravit.admin.adapter.gravit.dto.internal.GravitResponseDto;
import gravit.admin.global.exception.domain.RestApiException;
import gravit.admin.global.security.AdminVerificationCacheEvictor;
import gravit.admin.support.HttpStubServer;
import gravit.admin.support.HttpStubServer.StubResponse;
import gravit.admin.support.QaSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static gravit.admin.global.exception.domain.CustomErrorCode.MAIN_SERVER_RESULT_UNKNOWN;
import static gravit.admin.global.exception.domain.CustomErrorCode.MAIN_SERVER_UNREACHABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class GravitClientIntegrationTest {

    private static final String TOKEN = "admin-access-token";
    private static final String ADMIN_ME_BODY = "{\"adminId\":7}";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private AdminVerificationCacheEvictor adminVerificationCacheEvictor;

    @Autowired
    private HttpStubServer mainStubServer;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin", TOKEN, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("메인에 연결하지 못했을 때")
    class ConnectFailures {

        @Test
        void GET_me는_한_번_다시_보낸다() {
            // given
            AtomicInteger attempts = new AtomicInteger();
            mainStubServer.stub("GET", GravitClient.ADMIN_ME_PATH, StubResponse.json(200, ADMIN_ME_BODY));
            GravitClient client = clientFailingFirst(attempts, 1, new ConnectException("Connection refused"));

            // when
            GravitResponseDto response = client.exchange(요청(HttpMethod.GET, GravitClient.ADMIN_ME_PATH));

            // then
            assertSoftly(softly -> {
                softly.assertThat(attempts.get()).isEqualTo(2);
                softly.assertThat(response.status()).isEqualTo(200);
                softly.assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(ADMIN_ME_BODY);
            });
        }

        @Test
        void GET_me가_두_번_모두_실패하면_502_GRAVIT_5021() {
            // given
            AtomicInteger attempts = new AtomicInteger();
            GravitClient client = clientFailingFirst(attempts, 2, new ConnectException("Connection refused"));

            // when & then
            assertThatThrownBy(() -> client.exchange(요청(HttpMethod.GET, GravitClient.ADMIN_ME_PATH)))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(MAIN_SERVER_UNREACHABLE);
            assertThat(attempts.get()).isEqualTo(2);
        }

        @ParameterizedTest
        @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
        void 쓰기는_다시_보내지_않고_502_GRAVIT_5021(String method) {
            // given
            AtomicInteger attempts = new AtomicInteger();
            GravitClient client = clientFailingFirst(attempts, 1, new ConnectException("Connection refused"));

            // when & then
            assertThatThrownBy(() -> client.exchange(요청(HttpMethod.valueOf(method), "/api/v1/admin/notices/3")))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(MAIN_SERVER_UNREACHABLE);
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        void me가_아닌_GET은_다시_보내지_않는다() {
            // given
            AtomicInteger attempts = new AtomicInteger();
            GravitClient client = clientFailingFirst(attempts, 1, new ConnectException("Connection refused"));

            // when & then
            assertThatThrownBy(() -> client.exchange(요청(HttpMethod.GET, "/api/v1/admin/reports")))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(MAIN_SERVER_UNREACHABLE);
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        void 열려_있지_않은_포트로_연결하면_502_GRAVIT_5021() throws IOException {
            // given
            GravitClient client = new GravitClient(restClientBuilder.clone(), adminVerificationCacheEvictor,
                    new GravitProps("http://localhost:" + 닫힌_포트(), CONNECT_TIMEOUT, READ_TIMEOUT));

            // when & then
            assertThatThrownBy(() -> client.exchange(요청(HttpMethod.POST, "/api/v1/admin/notices")))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(MAIN_SERVER_UNREACHABLE);
        }
    }

    @Nested
    @DisplayName("연결한 뒤 응답을 확인하지 못했을 때")
    class UnknownResults {

        @Test
        void 응답_도중_연결이_끊기면_GET_me도_다시_보내지_않고_504_GRAVIT_5041() {
            // given
            AtomicInteger attempts = new AtomicInteger();
            GravitClient client = clientFailingFirst(attempts, 2, new IOException("Connection reset"));

            // when & then
            assertThatThrownBy(() -> client.exchange(요청(HttpMethod.GET, GravitClient.ADMIN_ME_PATH)))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(MAIN_SERVER_RESULT_UNKNOWN);
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        void 응답_타임아웃이면_GET_me도_다시_보내지_않고_504_GRAVIT_5041() {
            // given
            mainStubServer.stub("GET", GravitClient.ADMIN_ME_PATH, StubResponse.json(200, ADMIN_ME_BODY).delayed(Duration.ofSeconds(1)));
            GravitClient client = new GravitClient(restClientBuilder.clone(), adminVerificationCacheEvictor,
                    new GravitProps(mainStubServer.baseUrl(), CONNECT_TIMEOUT, READ_TIMEOUT));

            // when & then
            assertThatThrownBy(() -> client.exchange(요청(HttpMethod.GET, GravitClient.ADMIN_ME_PATH)))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(MAIN_SERVER_RESULT_UNKNOWN);
            assertThat(mainStubServer.requests("GET", GravitClient.ADMIN_ME_PATH)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("붙일 토큰을 꺼낼 때")
    class Tokens {

        @Test
        void 인증_객체가_없으면_메인을_부르지_않고_실패한다() {
            // given
            SecurityContextHolder.clearContext();
            GravitClient client = new GravitClient(restClientBuilder.clone(), adminVerificationCacheEvictor,
                    new GravitProps(mainStubServer.baseUrl(), CONNECT_TIMEOUT, READ_TIMEOUT));

            // when & then
            assertThatThrownBy(() -> client.exchange(요청(HttpMethod.GET, "/api/v1/admin/reports")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(TOKEN);
            assertThat(mainStubServer.requests()).isEmpty();
        }

        @Test
        void 호스트_끝의_슬래시는_경로와_겹치지_않는다() {
            // given
            mainStubServer.stub("GET", "/api/v1/admin/reports", StubResponse.json(200, "{}"));
            GravitClient client = new GravitClient(restClientBuilder.clone(), adminVerificationCacheEvictor,
                    new GravitProps(mainStubServer.baseUrl() + "/", CONNECT_TIMEOUT, READ_TIMEOUT));

            // when
            GravitResponseDto response = client.exchange(요청(HttpMethod.GET, "/api/v1/admin/reports"));

            // then
            assertSoftly(softly -> {
                softly.assertThat(response.status()).isEqualTo(200);
                softly.assertThat(mainStubServer.requests("GET", "/api/v1/admin/reports").getFirst().header("Authorization"))
                        .isEqualTo("Bearer " + TOKEN);
            });
        }
    }

    private GravitClient clientFailingFirst(
            AtomicInteger attempts,
            int failures,
            IOException failure
    ) {
        ClientHttpRequestInterceptor interceptor = (request, body, execution) -> {
            if (attempts.incrementAndGet() <= failures) {
                throw failure;
            }
            return execution.execute(request, body);
        };
        return new GravitClient(restClientBuilder.clone().requestInterceptor(interceptor), adminVerificationCacheEvictor,
                new GravitProps(mainStubServer.baseUrl(), CONNECT_TIMEOUT, READ_TIMEOUT));
    }

    private static GravitRequestDto 요청(
            HttpMethod method,
            String path
    ) {
        return GravitRequestDto.of(method, path, null, null, null, null);
    }

    private static int 닫힌_포트() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
