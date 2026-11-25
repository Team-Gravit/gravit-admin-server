package gravit.admin.global.dto.response;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * 메인 PageResponse.from(Spring Data Page)와 같은 값이 나오는지 본다. 크기 20, page는 1부터.
 */
class PageResponseTest {

    @ParameterizedTest(name = "page={0}, total={1} -> totalPages={2}, hasNext={3}")
    @CsvSource({
            "1, 0, 0, false",
            "5, 0, 0, false",
            "1, 1, 1, false",
            "1, 20, 1, false",
            "1, 21, 2, true",
            "2, 21, 2, false",
            "3, 21, 2, false",
            "2, 60, 3, true"
    })
    void 전체_페이지_수와_다음_페이지_여부를_Spring_Data_Page와_같게_센다(
            int page,
            long total,
            int expectedTotalPages,
            boolean expectedHasNext
    ) {
        // when
        PageResponse<String> response = PageResponse.of(page, 20, total, List.of());

        // then
        assertSoftly(softly -> {
            softly.assertThat(response.page()).isEqualTo(page);
            softly.assertThat(response.totalPages()).isEqualTo(expectedTotalPages);
            softly.assertThat(response.hasNext()).isEqualTo(expectedHasNext);
            softly.assertThat(response.totalElements()).isEqualTo(total);
        });
    }
}
