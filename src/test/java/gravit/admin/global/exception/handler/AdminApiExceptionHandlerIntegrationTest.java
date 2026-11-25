package gravit.admin.global.exception.handler;

import gravit.admin.insight.AdminApiErrorProbeController;
import gravit.admin.qa.QaApiErrorProbeController;
import gravit.admin.support.QaSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static gravit.admin.global.exception.domain.CustomErrorCode.ADMIN_API_INVALID_PARAMS;
import static gravit.admin.global.exception.domain.CustomErrorCode.INVALID_PARAMS;
import static gravit.admin.global.exception.domain.CustomErrorCode.RESOURCE_NOT_FOUND;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import({AdminApiErrorProbeController.class, QaApiErrorProbeController.class})
class AdminApiExceptionHandlerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String ADMIN_PATH = AdminApiErrorProbeController.BASE_PATH;
    private static final String QA_PATH = QaApiErrorProbeController.BASE_PATH;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("이관 모듈 Controller에서 오류가 나면")
    class AdminModules {

        @Test
        void Validated_파라미터_검증은_마지막_경로_이름_메시지_값_형식으로_답한다() throws Exception {
            // when & then
            mockMvc.perform(get(ADMIN_PATH + "/days").param("days", "0")
                            .header("Authorization", BEARER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(ADMIN_API_INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message", hasSize(1)))
                    .andExpect(jsonPath("$.message[0]", startsWith("[ days ][ ")))
                    .andExpect(jsonPath("$.message[0]", endsWith(" ][ 0 ]")));
        }

        @Test
        void 파라미터_타입_오류는_메인_메시지로_답한다() throws Exception {
            // when & then
            mockMvc.perform(get(ADMIN_PATH + "/items/abc")
                            .header("Authorization", BEARER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(ADMIN_API_INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message").value("유효성 검사 실패"));
        }

        @Test
        void RestApiException은_기존_처리를_따른다() throws Exception {
            // when & then
            mockMvc.perform(get(ADMIN_PATH + "/missing")
                            .header("Authorization", BEARER))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value(RESOURCE_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(RESOURCE_NOT_FOUND.getMessage()));
        }

        @Test
        void 본문_JSON_파싱_실패는_관리자_서버_형식_400을_유지한다() throws Exception {
            // when & then
            mockMvc.perform(post(ADMIN_PATH + "/items")
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not-json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message").value(INVALID_PARAMS.getMessage()));
        }
    }

    @Nested
    @DisplayName("QA Controller에서 같은 오류가 나면")
    class QaModules {

        @Test
        void Validated_파라미터_검증은_기존_전체_경로_메시지_형식을_유지한다() throws Exception {
            // when & then
            mockMvc.perform(get(QA_PATH + "/days").param("days", "0")
                            .header("Authorization", BEARER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message[0]", startsWith("[ getDays.days ][ ")))
                    .andExpect(jsonPath("$.message[0]", not(endsWith(" ][ 0 ]"))));
        }

        @Test
        void 파라미터_타입_오류는_기존_메시지를_유지한다() throws Exception {
            // when & then
            mockMvc.perform(get(QA_PATH + "/items/abc")
                            .header("Authorization", BEARER))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(INVALID_PARAMS.getCode()))
                    .andExpect(jsonPath("$.message").value(INVALID_PARAMS.getMessage()));
        }
    }
}
