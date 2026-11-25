package gravit.admin.support.report.controller;

import gravit.admin.support.HttpStubServer;
import gravit.admin.support.HttpStubServer.StubResponse;
import gravit.admin.support.QaSpringBootTest;
import gravit.admin.support.report.fixture.ReportMainDbFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static gravit.admin.global.exception.domain.CustomErrorCode.ADMIN_API_INVALID_PARAMS;
import static gravit.admin.global.exception.domain.CustomErrorCode.PAGE_MUST_START_FROM_1;
import static gravit.admin.global.exception.domain.CustomErrorCode.REPORT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(ReportMainDbFixture.class)
class ReportControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String ROUTE_HEADER = "X-Admin-Route";
    private static final String ROUTE_LOCAL = "local";
    private static final String BASE_PATH = "/api/v1/admin/reports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReportMainDbFixture fixture;

    @Autowired
    private HttpStubServer mainStubServer;

    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", BEARER).header(ROUTE_HEADER, ROUTE_LOCAL);
    }

    @Nested
    @DisplayName("헤더를 붙여 목록을 조회할 때")
    class List_ {

        @Test
        void 메인과_같은_페이지_응답과_항목_필드를_돌려준다() throws Exception {
            // given
            fixture.신고("TYPO_ERROR", false);

            // when & then
            mockMvc.perform(local(get(BASE_PATH)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"page":1,"totalPages":1,"hasNext":false,"totalElements":1,
                             "contents":[{"reportId":1,"reportType":"TYPO_ERROR","problemId":301,
                                          "isResolved":false,"submittedAt":"2026-11-18T10:15:30.123456"}]}
                            """, true));

            assertThat(mainStubServer.requests()).isEmpty();
        }

        @Test
        void 소수_초가_0이면_시각에서_소수부를_뺀다() throws Exception {
            // given
            fixture.신고("TYPO_ERROR", false, "내용", LocalDateTime.of(2026, 11, 18, 10, 15, 30));

            // when & then
            mockMvc.perform(local(get(BASE_PATH)))
                    .andExpect(jsonPath("$.contents[0].submittedAt").value("2026-11-18T10:15:30"));
        }

        @Test
        void 행이_없으면_전체_페이지_0과_빈_목록이다() throws Exception {
            // when & then
            mockMvc.perform(local(get(BASE_PATH)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"page\":1,\"totalPages\":0,\"hasNext\":false,\"totalElements\":0,\"contents\":[]}", true));
        }

        @ParameterizedTest(name = "page={0}")
        @CsvSource({
                "1, 20, true",
                "2, 1, false",
                "3, 0, false"
        })
        void 한_페이지는_20건이고_마지막_다음_페이지는_빈_목록이다(
                int page,
                int expectedSize,
                boolean expectedHasNext
        ) throws Exception {
            // given
            fixture.신고_여러_건(21);

            // when & then
            mockMvc.perform(local(get(BASE_PATH).param("page", String.valueOf(page))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.totalElements").value(21))
                    .andExpect(jsonPath("$.hasNext").value(expectedHasNext))
                    .andExpect(jsonPath("$.contents", hasSize(expectedSize)));
        }

        @Test
        void 유형과_처리_여부로_거른다() throws Exception {
            // given
            fixture.신고("TYPO_ERROR", false);
            fixture.신고("TYPO_ERROR", true);
            fixture.신고("CONTENT_ERROR", true);

            // when & then
            mockMvc.perform(local(get(BASE_PATH).param("reportType", "TYPO_ERROR").param("isResolved", "true")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.contents[0].reportId").value(2));
        }

        @Test
        void page가_1보다_작으면_PAGE_4001이다() throws Exception {
            // when & then
            mockMvc.perform(local(get(BASE_PATH).param("page", "0")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(PAGE_MUST_START_FROM_1.getCode()))
                    .andExpect(jsonPath("$.message").value(PAGE_MUST_START_FROM_1.getMessage()));
        }

        @ParameterizedTest(name = "{0}={1}")
        @CsvSource({
                "page, abc",
                "reportType, typo_error",
                "isResolved, maybe"
        })
        void 형식이_맞지_않는_파라미터는_메인_메시지로_400이다(
                String param,
                String value
        ) throws Exception {
            // when & then
            mockMvc.perform(local(get(BASE_PATH).param(param, value)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(ADMIN_API_INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message").value(ADMIN_API_INVALID_PARAMS.getMessage()));
        }
    }

    @Nested
    @DisplayName("헤더를 붙여 상세를 조회할 때")
    class Detail {

        @Test
        void 내용까지_메인과_같은_필드로_돌려준다() throws Exception {
            // given
            fixture.신고("ANSWER_ERROR", true, "정답이 틀렸습니다", ReportMainDbFixture.SUBMITTED_AT);

            // when & then
            mockMvc.perform(local(get(BASE_PATH + "/1")))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"reportId":1,"reportType":"ANSWER_ERROR","problemId":301,"content":"정답이 틀렸습니다",
                             "isResolved":true,"submittedAt":"2026-11-18T10:15:30.123456"}
                            """, true));
        }

        @Test
        void 없으면_REPORT_4041이다() throws Exception {
            // when & then
            mockMvc.perform(local(get(BASE_PATH + "/999")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value(REPORT_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(REPORT_NOT_FOUND.getMessage()));
        }

        @Test
        void ID가_숫자가_아니면_메인_메시지로_400이다() throws Exception {
            // when & then
            mockMvc.perform(local(get(BASE_PATH + "/abc")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ADMIN_API_INVALID_PARAMS.getMessage()));
        }
    }

    @Nested
    @DisplayName("메인에 남기는 요청은")
    class Forwarded {

        @Test
        void 헤더가_없는_목록_조회는_메인으로_넘긴다() throws Exception {
            // given
            String mainBody = "{\"page\":1,\"totalPages\":0,\"hasNext\":false,\"totalElements\":0,\"contents\":[]}";
            mainStubServer.stub("GET", BASE_PATH, StubResponse.json(200, mainBody));

            // when & then
            mockMvc.perform(get(BASE_PATH).header("Authorization", BEARER))
                    .andExpect(status().isOk())
                    .andExpect(content().string(mainBody));

            assertThat(mainStubServer.requests("GET", BASE_PATH)).hasSize(1);
        }

        @Test
        void 상태_변경_PATCH는_헤더가_있어도_메인으로_넘긴다() throws Exception {
            // given
            mainStubServer.stub("PATCH", BASE_PATH + "/1/status", StubResponse.status(200));

            // when & then
            mockMvc.perform(local(patch(BASE_PATH + "/1/status"))
                            .contentType("application/json")
                            .content("{\"isResolved\":true}"))
                    .andExpect(status().isOk());

            assertThat(mainStubServer.requests("PATCH", BASE_PATH + "/1/status")).hasSize(1);
        }
    }
}
