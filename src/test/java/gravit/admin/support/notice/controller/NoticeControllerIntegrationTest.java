package gravit.admin.support.notice.controller;

import gravit.admin.support.HttpStubServer;
import gravit.admin.support.HttpStubServer.StubResponse;
import gravit.admin.support.QaSpringBootTest;
import gravit.admin.support.notice.fixture.NoticeMainDbFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static gravit.admin.global.exception.domain.CustomErrorCode.ADMIN_API_INVALID_PARAMS;
import static gravit.admin.global.exception.domain.CustomErrorCode.NOTICE_NOT_FOUND;
import static gravit.admin.global.exception.domain.CustomErrorCode.PAGE_MUST_START_FROM_1;
import static gravit.admin.support.notice.fixture.NoticeMainDbFixture.BASE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(NoticeMainDbFixture.class)
class NoticeControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String ROUTE_HEADER = "X-Admin-Route";
    private static final String ROUTE_LOCAL = "local";
    private static final String BASE_PATH = "/api/v1/admin/notices";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NoticeMainDbFixture fixture;

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
            fixture.임시저장_공지("임시", BASE_TIME);

            // when & then
            mockMvc.perform(local(get(BASE_PATH)))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"page":1,"totalPages":1,"hasNext":false,"totalElements":1,
                             "contents":[{"noticeId":1,"title":"임시","status":"DRAFT","pinned":false,
                                          "publishedAt":null,"createdAt":"2026-11-01T09:00:00"}]}
                            """, true));

            assertThat(mainStubServer.requests()).isEmpty();
        }

        @Test
        void 페이지_경계에서도_핀이_첫_페이지_맨_위다() throws Exception {
            // given
            for (int i = 0; i < 21; i++) {
                fixture.게시된_공지("일반-" + i, false, BASE_TIME.plusMinutes(i));
            }
            fixture.게시된_공지("핀", true, BASE_TIME.minusDays(1));

            // when & then
            mockMvc.perform(local(get(BASE_PATH).param("page", "1")))
                    .andExpect(jsonPath("$.contents", hasSize(20)))
                    .andExpect(jsonPath("$.contents[0].title").value("핀"))
                    .andExpect(jsonPath("$.contents[1].title").value("일반-20"))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.hasNext").value(true));
            mockMvc.perform(local(get(BASE_PATH).param("page", "2")))
                    .andExpect(jsonPath("$.contents", hasSize(2)))
                    .andExpect(jsonPath("$.contents[1].title").value("일반-0"))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }

        @Test
        void page가_1보다_작으면_PAGE_4001이다() throws Exception {
            // when & then
            mockMvc.perform(local(get(BASE_PATH).param("page", "0")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(PAGE_MUST_START_FROM_1.getCode()))
                    .andExpect(jsonPath("$.message").value(PAGE_MUST_START_FROM_1.getMessage()));
        }

        @Test
        void page가_숫자가_아니면_메인_메시지로_400이다() throws Exception {
            // when & then
            mockMvc.perform(local(get(BASE_PATH).param("page", "abc")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(ADMIN_API_INVALID_PARAMS.getMessage()));
        }
    }

    @Nested
    @DisplayName("헤더를 붙여 상세를 조회할 때")
    class Detail {

        @Test
        void 요약과_본문까지_메인과_같은_필드로_돌려준다() throws Exception {
            // given
            fixture.게시된_공지("점검", true, BASE_TIME);

            // when & then
            mockMvc.perform(local(get(BASE_PATH + "/1")))
                    .andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"noticeId":1,"title":"점검","summary":"점검 요약","content":"점검 본문","status":"PUBLISHED",
                             "pinned":true,"publishedAt":"2026-11-01T09:00:00","createdAt":"2026-11-01T09:00:00"}
                            """, true));
        }

        @Test
        void 삭제한_공지는_NOTICE_4041이다() throws Exception {
            // given
            fixture.삭제된_공지("지운 공지");

            // when & then
            mockMvc.perform(local(get(BASE_PATH + "/1")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value(NOTICE_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(NOTICE_NOT_FOUND.getMessage()));
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
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource({
                "POST, /api/v1/admin/notices, 201",
                "PATCH, /api/v1/admin/notices/1, 200",
                "DELETE, /api/v1/admin/notices/1, 204"
        })
        void 생성_수정_삭제는_헤더가_있어도_메인으로_넘긴다(
                String method,
                String path,
                int mainStatus
        ) throws Exception {
            // given
            mainStubServer.stub(method, path, StubResponse.status(mainStatus));

            // when & then
            mockMvc.perform(local(request(HttpMethod.valueOf(method), path))
                            .contentType("application/json")
                            .content("{\"title\":\"제목\"}"))
                    .andExpect(status().is(mainStatus));

            assertThat(mainStubServer.requests(method, path)).hasSize(1);
        }
    }
}
