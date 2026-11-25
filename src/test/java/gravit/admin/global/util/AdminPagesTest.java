package gravit.admin.global.util;

import gravit.admin.global.exception.domain.RestApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static gravit.admin.global.exception.domain.CustomErrorCode.PAGE_MUST_START_FROM_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPagesTest {

    @Test
    void 페이지는_1부터이고_한_페이지는_20건이다() {
        // when & then
        assertThat(AdminPages.offsetOf(1)).isZero();
        assertThat(AdminPages.offsetOf(3)).isEqualTo(40);
    }

    @Test
    void 큰_페이지_번호도_넘치지_않는다() {
        // when & then
        assertThat(AdminPages.offsetOf(Integer.MAX_VALUE)).isEqualTo((Integer.MAX_VALUE - 1L) * 20);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 페이지가_1보다_작으면_PAGE_4001을_던진다(int page) {
        // when & then
        assertThatThrownBy(() -> AdminPages.offsetOf(page))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(PAGE_MUST_START_FROM_1);
    }
}
