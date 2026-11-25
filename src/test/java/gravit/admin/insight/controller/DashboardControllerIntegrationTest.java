package gravit.admin.insight.controller;

import gravit.admin.insight.fixture.DashboardMainDbFixture;
import gravit.admin.support.HttpStubServer;
import gravit.admin.support.HttpStubServer.StubResponse;
import gravit.admin.support.QaSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static gravit.admin.global.exception.domain.CustomErrorCode.ADMIN_API_INVALID_PARAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(DashboardMainDbFixture.class)
class DashboardControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String ROUTE_HEADER = "X-Admin-Route";
    private static final String ROUTE_LOCAL = "local";
    private static final String BASE_PATH = "/api/v1/admin/dashboard";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardMainDbFixture fixture;

    @Autowired
    private HttpStubServer mainStubServer;

    @Nested
    @DisplayName("X-Admin-Route: local 헤더가 있으면")
    class LocalRoute {

        @Test
        void 요약을_메인과_같은_필드로_돌려준다() throws Exception {
            // given
            fixture.회원("ACTIVE");
            fixture.스테이징_라벨("PENDING");
            fixture.신고(false);

            // when & then
            mockMvc.perform(get(BASE_PATH + "/summary")
                            .header("Authorization", BEARER)
                            .header(ROUTE_HEADER, ROUTE_LOCAL))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"totalUsers\":1,\"pendingLabelsCount\":1,\"unresolvedReportsCount\":1}", true));

            assertThat(mainStubServer.requests()).isEmpty();
        }

        @Test
        void 일별_추이는_기본_30일이고_날짜는_yyyy_MM_dd다() throws Exception {
            // given
            fixture.활동(1L, LocalDate.of(2026, 10, 16));

            // when & then
            mockMvc.perform(get(BASE_PATH + "/active-users/daily")
                            .header("Authorization", BEARER)
                            .header(ROUTE_HEADER, ROUTE_LOCAL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points", hasSize(30)))
                    .andExpect(jsonPath("$.points[0].date").value("2026-09-17"))
                    .andExpect(jsonPath("$.points[29].date").value("2026-10-16"))
                    .andExpect(jsonPath("$.points[29].dau").value(1));
        }

        @Test
        void 월별_추이는_기본_12개월이고_이번_달만_진행_중이다() throws Exception {
            // when & then
            mockMvc.perform(get(BASE_PATH + "/active-users/monthly")
                            .header("Authorization", BEARER)
                            .header(ROUTE_HEADER, ROUTE_LOCAL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points", hasSize(12)))
                    .andExpect(jsonPath("$.points[0].month").value("2025-11"))
                    .andExpect(jsonPath("$.points[0].inProgress").value(false))
                    .andExpect(jsonPath("$.points[11].month").value("2026-10"))
                    .andExpect(jsonPath("$.points[11].mau").value(0))
                    .andExpect(jsonPath("$.points[11].inProgress").value(true));
        }

        @ParameterizedTest(name = "{0}={1}")
        @CsvSource({
                "active-users/daily, days, 0",
                "active-users/daily, days, 366",
                "active-users/monthly, months, 0",
                "active-users/monthly, months, 37"
        })
        void 범위를_벗어나면_메인_형식의_400을_돌려준다(
                String path,
                String param,
                String value
        ) throws Exception {
            // when & then
            mockMvc.perform(get(BASE_PATH + "/" + path).param(param, value)
                            .header("Authorization", BEARER)
                            .header(ROUTE_HEADER, ROUTE_LOCAL))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(ADMIN_API_INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message", hasSize(1)))
                    .andExpect(jsonPath("$.message[0]", startsWith("[ " + param + " ][ ")))
                    .andExpect(jsonPath("$.message[0]", endsWith(" ][ " + value + " ]")));
        }

        @ParameterizedTest(name = "{0}=1")
        @CsvSource({
                "active-users/daily, days, 1",
                "active-users/daily, days, 365",
                "active-users/monthly, months, 1",
                "active-users/monthly, months, 36"
        })
        void 범위_양끝은_받아들인다(
                String path,
                String param,
                int value
        ) throws Exception {
            // when & then
            mockMvc.perform(get(BASE_PATH + "/" + path).param(param, String.valueOf(value))
                            .header("Authorization", BEARER)
                            .header(ROUTE_HEADER, ROUTE_LOCAL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points", hasSize(value)));
        }

        @Test
        void 숫자가_아닌_파라미터는_메인_메시지로_400을_돌려준다() throws Exception {
            // when & then
            mockMvc.perform(get(BASE_PATH + "/active-users/daily").param("days", "abc")
                            .header("Authorization", BEARER)
                            .header(ROUTE_HEADER, ROUTE_LOCAL))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(ADMIN_API_INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message").value(ADMIN_API_INVALID_PARAMS.getMessage()));
        }
    }

    @Nested
    @DisplayName("헤더가 없으면(웹 요청)")
    class Forwarded {

        @Test
        void 전달_계층이_메인으로_넘기고_메인_응답을_그대로_돌려준다() throws Exception {
            // given
            String mainBody = "{\"totalUsers\":99,\"pendingLabelsCount\":0,\"unresolvedReportsCount\":0}";
            mainStubServer.stub("GET", BASE_PATH + "/summary", StubResponse.json(200, mainBody));

            // when & then
            mockMvc.perform(get(BASE_PATH + "/summary")
                            .header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(content().string(mainBody));

            assertThat(mainStubServer.requests("GET", BASE_PATH + "/summary")).hasSize(1);
        }

        @Test
        void 범위를_벗어난_요청도_메인이_판단한다() throws Exception {
            // given
            String mainBody = "{\"error\":\"GLOBAL_4001\",\"message\":[\"[ days ][ 1 이상이어야 합니다 ][ 0 ]\"]}";
            mainStubServer.stub("GET", BASE_PATH + "/active-users/daily", StubResponse.json(400, mainBody));

            // when & then
            mockMvc.perform(get(BASE_PATH + "/active-users/daily").param("days", "0")
                            .header("Authorization", BEARER))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(mainBody));
        }
    }
}
