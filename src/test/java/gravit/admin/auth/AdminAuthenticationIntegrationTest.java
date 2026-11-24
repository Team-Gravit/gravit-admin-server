package gravit.admin.auth;

import gravit.admin.auth.filter.AdminTokenAuthenticationFilter;
import gravit.admin.auth.fixture.AccessTokenFixture;
import gravit.admin.auth.principal.LoginAdmin;
import gravit.admin.auth.support.AdminVerifier;
import gravit.admin.auth.support.ErrorResponseWriter;
import gravit.admin.global.security.AdminVerificationCacheEvictor;
import gravit.admin.job.domain.JobType;
import gravit.admin.job.service.JobCommandService;
import gravit.admin.support.AdminMeStubServer;
import gravit.admin.support.QaSpringBootTest;
import gravit.admin.support.TestClock;
import gravit.admin.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
class AdminAuthenticationIntegrationTest {

    private static final String TOKEN = "admin-access-token";
    private static final String BEARER = "Bearer " + TOKEN;

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminMeStubServer adminMeStubServer;

    @Autowired
    private TestClock testClock;

    @Autowired
    private AdminVerifier adminVerifier;

    @Autowired
    private ErrorResponseWriter errorResponseWriter;

    @Autowired
    private AdminVerificationCacheEvictor adminVerificationCacheEvictor;

    @Nested
    @DisplayName("관리자 판정 결과에 따라")
    class 판정_결과 {

        @Test
        void 토큰이_없으면_401_AUTH_4011을_반환하고_인증_서버를_호출하지_않는다() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/jobs/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("AUTH_4011"));

            assertThat(adminMeStubServer.hits()).isZero();
        }

        @Test
        void 인증_서버가_200이면_요청이_통과하고_토큰을_그대로_전달한다() throws Exception {
            // given
            long jobId = jobCommandService.enqueue(JobType.SPEC_LOAD, null, Map.of());

            // when & then
            mockMvc.perform(get("/api/v1/jobs/" + jobId).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value((int) jobId));

            assertThat(adminMeStubServer.authorizations()).containsExactly(BEARER);
        }

        @Test
        void 인증_서버가_401이면_401_AUTH_4012를_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(401, "{}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("AUTH_4012"));
        }

        @Test
        void 인증_서버가_403이면_403_AUTH_4031을_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(403, "{}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("AUTH_4031"));
        }

        @Test
        void 인증_서버가_그_외_상태를_반환하면_503_AUTH_5031을_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(500, "{}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("AUTH_5031"));
        }

        @Test
        void 인증_서버가_제한_시간_안에_응답하지_않으면_503_AUTH_5031을_반환한다() throws Exception {
            // given
            adminMeStubServer.delay(Duration.ofSeconds(3));

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("AUTH_5031"));
        }

        @Test
        void 성공_응답에_adminId가_없으면_503_AUTH_5031을_반환한다() throws Exception {
            // given
            adminMeStubServer.respond(200, "{\"nickname\": \"운영자\"}");

            // when & then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("AUTH_5031"));
        }
    }

    @Nested
    @DisplayName("판정 결과를 캐시할 때")
    class 캐시 {

        @Test
        void 성공_판정은_TTL_동안_재사용하고_만료_후_다시_확인한다() throws Exception {
            // given
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // when
            testClock.advance(Duration.ofSeconds(299));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));
            int hitsWithinTtl = adminMeStubServer.hits();

            testClock.advance(Duration.ofSeconds(2));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // then
            assertThat(hitsWithinTtl).isEqualTo(1);
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        @Test
        void 실패_판정은_캐시하지_않는다() throws Exception {
            // given
            adminMeStubServer.respond(401, "{}");
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // when
            adminMeStubServer.respond(200, AdminMeStubServer.ADMIN_BODY);
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("JOB_4041"));

            // then
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        @Test
        void 토큰_exp가_TTL보다_이르면_exp까지만_재사용한다() throws Exception {
            // given
            String token = AccessTokenFixture.만료_시각을_담은_토큰(TestClockConfig.BASE_INSTANT.plusSeconds(100));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));

            // when
            testClock.advance(Duration.ofSeconds(99));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));
            int hitsBeforeExp = adminMeStubServer.hits();

            testClock.advance(Duration.ofSeconds(1));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));

            // then
            assertThat(hitsBeforeExp).isEqualTo(1);
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        @Test
        void 토큰_exp가_TTL보다_늦으면_TTL까지_재사용한다() throws Exception {
            // given
            String token = AccessTokenFixture.만료_시각을_담은_토큰(TestClockConfig.BASE_INSTANT.plus(Duration.ofHours(2)));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));

            // when
            testClock.advance(Duration.ofSeconds(299));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));
            int hitsWithinTtl = adminMeStubServer.hits();

            testClock.advance(Duration.ofSeconds(2));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));

            // then
            assertThat(hitsWithinTtl).isEqualTo(1);
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        @Test
        void 토큰_exp가_이미_지났으면_판정대로_통과시키고_캐시하지_않는다() throws Exception {
            // given
            String token = AccessTokenFixture.만료_시각을_담은_토큰(TestClockConfig.BASE_INSTANT.minusSeconds(1));

            // when
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("JOB_4041"));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));

            // then
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        @ParameterizedTest
        @MethodSource("exp를_읽을_수_없는_토큰")
        void exp를_읽을_수_없는_토큰은_TTL까지_재사용한다(String token) throws Exception {
            // given
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("JOB_4041"));

            // when
            testClock.advance(Duration.ofSeconds(299));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));
            int hitsWithinTtl = adminMeStubServer.hits();

            testClock.advance(Duration.ofSeconds(2));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + token));

            // then
            assertThat(hitsWithinTtl).isEqualTo(1);
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        static Stream<Arguments> exp를_읽을_수_없는_토큰() {
            return Stream.of(
                    argumentSet("exp 없음", AccessTokenFixture.만료_시각이_없는_토큰()),
                    argumentSet("exp가 숫자가 아님", AccessTokenFixture.만료_시각이_숫자가_아닌_토큰()),
                    argumentSet("exp가 시각 범위를 넘음", AccessTokenFixture.범위를_넘는_만료_시각을_담은_토큰()),
                    argumentSet("본문이 JSON이 아님", AccessTokenFixture.본문이_JSON이_아닌_토큰()),
                    argumentSet("본문이 객체가 아님", AccessTokenFixture.본문이_객체가_아닌_토큰()),
                    argumentSet("본문이 base64url이 아님", AccessTokenFixture.본문이_base64url이_아닌_토큰()),
                    argumentSet("마침표 없음", AccessTokenFixture.마침표가_없는_토큰()),
                    argumentSet("조각이 다섯", AccessTokenFixture.조각이_다섯인_토큰()));
        }
    }

    @Nested
    @DisplayName("쓰기 요청을 판정할 때")
    class 쓰기_요청 {

        @ParameterizedTest
        @MethodSource("쓰기_메서드와_경로")
        void 캐시를_건너뛰고_매번_판정하며_결과로_캐시를_갱신한다(
                HttpMethod method,
                String path
        ) throws Exception {
            // given
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // when
            mockMvc.perform(request(method, path).header("Authorization", BEARER));
            mockMvc.perform(request(method, path).header("Authorization", BEARER));
            int hitsAfterWrites = adminMeStubServer.hits();

            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // then
            assertThat(hitsAfterWrites).isEqualTo(3);
            assertThat(adminMeStubServer.hits()).isEqualTo(3);
        }

        @ParameterizedTest
        @CsvSource({"401, 401, AUTH_4012", "403, 403, AUTH_4031"})
        void 판정이_거절되면_토큰_캐시를_지워_이후_조회도_다시_판정한다(
                int adminMeStatus,
                int expectedStatus,
                String expectedError
        ) throws Exception {
            // given
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));
            adminMeStubServer.respond(adminMeStatus, "{}");

            // when
            mockMvc.perform(post("/api/v1/jobs/1/cancel").header("Authorization", BEARER))
                    .andExpect(status().is(expectedStatus))
                    .andExpect(jsonPath("$.error").value(expectedError));

            // then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().is(expectedStatus))
                    .andExpect(jsonPath("$.error").value(expectedError));
            assertThat(adminMeStubServer.hits()).isEqualTo(3);
        }

        @Test
        void 인증_서버_장애로_판정하지_못하면_토큰_캐시를_남긴다() throws Exception {
            // given
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));
            adminMeStubServer.respond(500, "{}");

            // when
            mockMvc.perform(post("/api/v1/jobs/1/cancel").header("Authorization", BEARER))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("AUTH_5031"));

            // then
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("JOB_4041"));
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        static Stream<Arguments> 쓰기_메서드와_경로() {
            return Stream.of(
                    argumentSet("POST", HttpMethod.POST, "/api/v1/jobs/1/cancel"),
                    argumentSet("PUT", HttpMethod.PUT, "/api/v1/checklist-items/1/cells/WEB"),
                    argumentSet("PATCH", HttpMethod.PATCH, "/api/v1/checklist-items/1"),
                    argumentSet("DELETE", HttpMethod.DELETE, "/api/v1/checklist-items/1"));
        }
    }

    @Nested
    @DisplayName("토큰 단위로 캐시를 지울 때")
    class 캐시_삭제 {

        @Test
        void 지운_토큰은_다음_요청에서_다시_판정한다() throws Exception {
            // given
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // when
            adminVerificationCacheEvictor.evict(TOKEN);
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // then
            assertThat(adminMeStubServer.hits()).isEqualTo(2);
        }

        @Test
        void 다른_토큰의_캐시는_남긴다() throws Exception {
            // given
            String otherToken = "other-admin-access-token";
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + otherToken));

            // when
            adminVerificationCacheEvictor.evict(TOKEN);
            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", "Bearer " + otherToken));
            int hitsForOtherToken = adminMeStubServer.hits();

            mockMvc.perform(get("/api/v1/jobs/1").header("Authorization", BEARER));

            // then
            assertThat(hitsForOtherToken).isEqualTo(2);
            assertThat(adminMeStubServer.hits()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("판정을 통과한 토큰을 보관할 때")
    @ExtendWith(OutputCaptureExtension.class)
    class 토큰_보관 {

        @Test
        void 인증_객체_자격_증명_칸에_토큰_원문을_담는다() throws Exception {
            // given
            AdminTokenAuthenticationFilter filter = new AdminTokenAuthenticationFilter(adminVerifier, errorResponseWriter);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs/1");
            request.addHeader("Authorization", BEARER);
            AtomicReference<Authentication> authenticated = new AtomicReference<>();

            // when
            try {
                filter.doFilter(request, new MockHttpServletResponse(),
                        (req, res) -> authenticated.set(SecurityContextHolder.getContext().getAuthentication()));
            } finally {
                SecurityContextHolder.clearContext();
            }

            // then
            Authentication authentication = authenticated.get();
            assertSoftly(softly -> {
                softly.assertThat(authentication.getCredentials()).isEqualTo(TOKEN);
                softly.assertThat(authentication.getPrincipal())
                        .isInstanceOfSatisfying(LoginAdmin.class, admin -> assertThat(admin.adminId()).isEqualTo(7L));
                softly.assertThat(authentication.toString()).doesNotContain(TOKEN);
            });
        }

        @Test
        void 판정_결과와_상관없이_토큰_원문을_로그와_응답에_남기지_않는다(CapturedOutput output) throws Exception {
            // given
            String token = AccessTokenFixture.만료_시각을_담은_토큰(TestClockConfig.BASE_INSTANT.plus(Duration.ofHours(2)));
            String brokenToken = AccessTokenFixture.본문이_JSON이_아닌_토큰();
            List<String> responseBodies = new ArrayList<>();

            // when
            responseBodies.add(perform(get("/api/v1/jobs/1"), token));
            responseBodies.add(perform(post("/api/v1/jobs/1/cancel"), token));
            adminVerificationCacheEvictor.evict(token);
            responseBodies.add(perform(get("/api/v1/jobs/1"), brokenToken));

            adminMeStubServer.respond(401, "{}");
            responseBodies.add(perform(post("/api/v1/jobs/1/cancel"), token));
            adminMeStubServer.respond(403, "{}");
            responseBodies.add(perform(post("/api/v1/jobs/1/cancel"), token));
            adminMeStubServer.respond(500, "{}");
            responseBodies.add(perform(post("/api/v1/jobs/1/cancel"), token));
            adminMeStubServer.respond(200, "{\"nickname\": \"운영자\"}");
            responseBodies.add(perform(post("/api/v1/jobs/1/cancel"), token));
            adminMeStubServer.respond(200, AdminMeStubServer.ADMIN_BODY);
            adminMeStubServer.delay(Duration.ofSeconds(3));
            responseBodies.add(perform(post("/api/v1/jobs/1/cancel"), token));

            // then
            assertSoftly(softly -> {
                softly.assertThat(output.getAll()).contains("status=500", "인증 서버 호출 실패");
                softly.assertThat(output.getAll()).doesNotContain(token, brokenToken, AccessTokenFixture.SIGNATURE);
                softly.assertThat(responseBodies).hasSize(8)
                        .allSatisfy(body -> assertThat(body).doesNotContain(token, brokenToken, AccessTokenFixture.SIGNATURE));
            });
        }

        private String perform(
                MockHttpServletRequestBuilder request,
                String token
        ) throws Exception {
            return mockMvc.perform(request.header("Authorization", "Bearer " + token))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        }
    }

    @Test
    void 헬스_체크는_인증_없이_200을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        assertThat(adminMeStubServer.hits()).isZero();
    }
}
