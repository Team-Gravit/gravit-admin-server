package gravit.admin.adapter.gravit.controller;

import gravit.admin.auth.support.AdminVerificationCache;
import gravit.admin.support.AdminMeStubServer;
import gravit.admin.support.HttpStubServer;
import gravit.admin.support.HttpStubServer.RecordedRequest;
import gravit.admin.support.HttpStubServer.StubResponse;
import gravit.admin.support.QaSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(AdminForwardControllerIntegrationTest.LocalNoticeController.class)
class AdminForwardControllerIntegrationTest {

    private static final String TOKEN = "admin-access-token";
    private static final String BEARER = "Bearer " + TOKEN;
    private static final String JSON_UTF8 = "application/json;charset=UTF-8";
    private static final String MAIN_BODY = "{\"b\":1,  \"a\":null,\"createdAt\":\"2026-11-18T10:15:30.123456\"}";
    private static final String LOCAL_BODY = "local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HttpStubServer mainStubServer;

    @Autowired
    private AdminMeStubServer adminMeStubServer;

    @Autowired
    private AdminVerificationCache adminVerificationCache;

    @Nested
    @DisplayName("메서드별로 전달할 때")
    class Methods {

        @ParameterizedTest
        @ValueSource(strings = {"POST", "PUT", "PATCH"})
        void 본문이_있는_쓰기는_메서드_경로_본문을_그대로_넘긴다(String method) throws Exception {
            // given
            String requestBody = "{\"title\":\"공지\",  \"pinned\":true}";
            mainStubServer.stub(method, "/api/v1/admin/notices/3", StubResponse.json(200, MAIN_BODY));

            // when
            MockHttpServletResponse response = mockMvc.perform(request(HttpMethod.valueOf(method), "/api/v1/admin/notices/3")
                            .header("Authorization", BEARER)
                            .contentType(JSON_UTF8)
                            .content(requestBody))
                    .andReturn().getResponse();

            // then
            List<RecordedRequest> received = mainStubServer.requests(method, "/api/v1/admin/notices/3");
            assertSoftly(softly -> {
                softly.assertThat(received).hasSize(1);
                softly.assertThat(received.getFirst().body()).isEqualTo(requestBody);
                softly.assertThat(response.getStatus()).isEqualTo(200);
                softly.assertThat(response.getContentAsByteArray()).isEqualTo(MAIN_BODY.getBytes(StandardCharsets.UTF_8));
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"GET", "DELETE"})
        void 본문이_없는_요청은_본문_없이_넘긴다(String method) throws Exception {
            // given
            mainStubServer.stub(method, "/api/v1/admin/reports/3", StubResponse.json(200, MAIN_BODY));

            // when
            MockHttpServletResponse response = mockMvc.perform(request(HttpMethod.valueOf(method), "/api/v1/admin/reports/3")
                            .header("Authorization", BEARER))
                    .andReturn().getResponse();

            // then
            List<RecordedRequest> received = mainStubServer.requests(method, "/api/v1/admin/reports/3");
            assertSoftly(softly -> {
                softly.assertThat(received).hasSize(1);
                softly.assertThat(received.getFirst().body()).isEmpty();
                softly.assertThat(received.getFirst().hasHeader("Content-Type")).isFalse();
                softly.assertThat(response.getStatus()).isEqualTo(200);
                softly.assertThat(response.getContentAsByteArray()).isEqualTo(MAIN_BODY.getBytes(StandardCharsets.UTF_8));
            });
        }

        @Test
        void 쿼리_문자열은_인코딩을_바꾸지_않고_넘긴다() throws Exception {
            // given
            String rawQuery = "page=2&search=%EA%B9%80+a%2Bb&status=ACTIVE&status=SUSPENDED";
            mainStubServer.stub("GET", "/api/v1/admin/users", StubResponse.json(200, MAIN_BODY));

            // when
            mockMvc.perform(get(URI.create("/api/v1/admin/users?" + rawQuery))
                            .header("Authorization", BEARER))
                    .andExpect(status().isOk());

            // then
            assertThat(mainStubServer.requests("GET", "/api/v1/admin/users"))
                    .singleElement()
                    .extracting(RecordedRequest::query)
                    .isEqualTo(rawQuery);
        }
    }

    @Nested
    @DisplayName("요청 헤더를 넘길 때")
    class RequestHeaders {

        @Test
        void Authorization_Content_Type_Accept만_넘기고_쿠키와_Host는_넘기지_않는다() throws Exception {
            // given
            mainStubServer.stub("POST", "/api/v1/admin/notices", StubResponse.json(201, MAIN_BODY));

            // when
            mockMvc.perform(request(HttpMethod.POST, "/api/v1/admin/notices")
                            .header("Authorization", BEARER)
                            .header("Cookie", "refreshToken=secret-refresh")
                            .header("Host", "grav-it-admin.example")
                            .header("X-Forwarded-For", "10.0.0.1")
                            .header("X-Admin-Route", "local")
                            .header("Accept", "application/json, text/plain, */*")
                            .contentType(JSON_UTF8)
                            .content("{}"))
                    .andExpect(status().isCreated());

            // then
            RecordedRequest received = mainStubServer.requests("POST", "/api/v1/admin/notices").getFirst();
            assertSoftly(softly -> {
                softly.assertThat(received.header("Authorization")).isEqualTo(BEARER);
                softly.assertThat(received.header("Content-Type")).isEqualTo(JSON_UTF8);
                softly.assertThat(received.header("Accept")).isEqualTo("application/json, text/plain, */*");
                softly.assertThat(received.hasHeader("Cookie")).isFalse();
                softly.assertThat(received.hasHeader("X-Forwarded-For")).isFalse();
                softly.assertThat(received.hasHeader("X-Admin-Route")).isFalse();
                softly.assertThat(received.header("Host")).isEqualTo(URI.create(mainStubServer.baseUrl()).getAuthority());
            });
        }
    }

    @Nested
    @DisplayName("메인 응답을 돌려줄 때")
    class MainResponses {

        @Test
        void 상태_Content_Type_본문_바이트를_그대로_돌려준다() throws Exception {
            // given
            byte[] body = MAIN_BODY.getBytes(StandardCharsets.UTF_8);
            mainStubServer.stub("GET", "/api/v1/admin/reports/1", new StubResponse(200, Map.of("Content-Type", JSON_UTF8), body, Duration.ZERO));

            // when
            MockHttpServletResponse response = mockMvc.perform(get("/api/v1/admin/reports/1")
                            .header("Authorization", BEARER))
                    .andReturn().getResponse();

            // then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatus()).isEqualTo(200);
                softly.assertThat(response.getHeader("Content-Type")).isEqualTo(JSON_UTF8);
                softly.assertThat(response.getContentAsByteArray()).isEqualTo(body);
            });
        }

        @Test
        void 메인_오류_본문을_상태와_함께_그대로_돌려준다() throws Exception {
            // given
            String errorBody = "{\"error\":\"NOTICE_4041\",\"message\":\"존재하지 않는 공지입니다.\"}";
            mainStubServer.stub("DELETE", "/api/v1/admin/notices/99", StubResponse.json(404, errorBody));

            // when & then
            mockMvc.perform(request(HttpMethod.DELETE, "/api/v1/admin/notices/99")
                            .header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(content().bytes(errorBody.getBytes(StandardCharsets.UTF_8)));
        }

        @Test
        void 메인_204는_본문과_Content_Type_없이_돌려준다() throws Exception {
            // given
            mainStubServer.stub("DELETE", "/api/v1/admin/inquiries/5/answer", new StubResponse(204, Map.of(), new byte[0], Duration.ZERO));

            // when
            MockHttpServletResponse response = mockMvc.perform(request(HttpMethod.DELETE, "/api/v1/admin/inquiries/5/answer")
                            .header("Authorization", BEARER))
                    .andReturn().getResponse();

            // then
            assertSoftly(softly -> {
                softly.assertThat(response.getStatus()).isEqualTo(204);
                softly.assertThat(response.getContentAsByteArray()).isEmpty();
                softly.assertThat(response.getContentType()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("메인이 인증을 거부할 때")
    class Rejections {

        @ParameterizedTest
        @ValueSource(ints = {401, 403})
        void 판정_캐시를_지우고_메인_본문을_그대로_돌려준다(int mainStatus) throws Exception {
            // given
            String errorBody = mainStatus == 401
                    ? "{\"error\":\"JWT_4012\",\"message\":\"만료된 토큰입니다.\"}"
                    : "{\"error\":\"A_D_403\",\"message\":\"접근 권한이 없습니다.\"}";
            mainStubServer.stub("GET", "/api/v1/admin/dashboard/summary", StubResponse.json(mainStatus, errorBody));

            // when
            mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                            .header("Authorization", BEARER))
                    .andExpect(status().is(mainStatus))
                    .andExpect(content().bytes(errorBody.getBytes(StandardCharsets.UTF_8)));

            // then
            assertThat(adminVerificationCache.get(TOKEN)).isEmpty();
        }

        @Test
        void 메인이_성공하면_판정_캐시를_남긴다() throws Exception {
            // given
            mainStubServer.stub("GET", "/api/v1/admin/dashboard/summary", StubResponse.json(200, MAIN_BODY));

            // when
            mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                            .header("Authorization", BEARER))
                    .andExpect(status().isOk());

            // then
            assertThat(adminVerificationCache.get(TOKEN)).isPresent();
        }

        @Test
        void 전달되는_쓰기도_요청마다_판정한다() throws Exception {
            // given
            mainStubServer.stub("PATCH", "/api/v1/admin/reports/1/status", StubResponse.json(200, MAIN_BODY));

            // when
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(patch("/api/v1/admin/reports/1/status")
                                .header("Authorization", BEARER)
                                .contentType(JSON_UTF8)
                                .content("{\"isResolved\":true}"))
                        .andExpect(status().isOk());
            }

            // then
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("메인 응답이 늦을 때")
    @ExtendWith(OutputCaptureExtension.class)
    class Timeouts {

        @Test
        void 응답_타임아웃이면_504_GRAVIT_5041을_돌려주고_다시_보내지_않는다(CapturedOutput output) throws Exception {
            // given
            mainStubServer.stub("POST", "/api/v1/admin/notices", StubResponse.json(201, MAIN_BODY).delayed(Duration.ofSeconds(1)));

            // when
            mockMvc.perform(request(HttpMethod.POST, "/api/v1/admin/notices")
                            .header("Authorization", BEARER)
                            .contentType(JSON_UTF8)
                            .content("{}"))
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.error").value("GRAVIT_5041"))
                    .andExpect(jsonPath("$.message").value("메인 서버 응답이 늦어 처리 여부를 확인하지 못했습니다. 목록을 다시 불러와 확인하세요."));

            // then
            assertSoftly(softly -> {
                softly.assertThat(mainStubServer.requests("POST", "/api/v1/admin/notices")).hasSize(1);
                softly.assertThat(output.getAll()).doesNotContain(TOKEN);
            });
        }
    }

    @Nested
    @DisplayName("같은 경로를 관리자 서버가 일부 메서드만 처리할 때")
    class SamePathOtherMethod {

        @Test
        void 구체_Controller가_잡은_GET은_메인으로_넘기지_않는다() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/admin/notices/7")
                            .header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(content().string(LOCAL_BODY + 7));

            assertThat(mainStubServer.requests()).isEmpty();
        }

        @Test
        void 같은_경로의_PATCH는_전달_계층이_메인으로_넘긴다() throws Exception {
            // given
            mainStubServer.stub("PATCH", "/api/v1/admin/notices/7", StubResponse.json(200, MAIN_BODY));

            // when & then
            mockMvc.perform(patch("/api/v1/admin/notices/7")
                            .header("Authorization", BEARER)
                            .contentType(JSON_UTF8)
                            .content("{\"status\":\"PUBLISHED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(MAIN_BODY));

            assertThat(mainStubServer.requests("PATCH", "/api/v1/admin/notices/7")).hasSize(1);
        }
    }

    @RestController
    static class LocalNoticeController {

        @GetMapping("/api/v1/admin/notices/{noticeId}")
        String getNotice(@PathVariable("noticeId") long noticeId) {
            return LOCAL_BODY + noticeId;
        }
    }
}
