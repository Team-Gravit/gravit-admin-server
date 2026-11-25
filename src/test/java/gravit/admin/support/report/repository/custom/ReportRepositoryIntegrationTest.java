package gravit.admin.support.report.repository.custom;

import gravit.admin.support.QaSpringBootTest;
import gravit.admin.support.report.domain.ReportType;
import gravit.admin.support.report.dto.response.ReportDetailResponse;
import gravit.admin.support.report.dto.response.ReportListItemResponse;
import gravit.admin.support.report.fixture.ReportMainDbFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static gravit.admin.support.report.fixture.ReportMainDbFixture.SUBMITTED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
@Import(ReportMainDbFixture.class)
class ReportRepositoryIntegrationTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportMainDbFixture fixture;

    @Nested
    @DisplayName("목록을 조회할 때")
    class Page {

        @BeforeEach
        void setUp() {
            fixture.신고("TYPO_ERROR", false);
            fixture.신고("CONTENT_ERROR", true);
            fixture.신고("TYPO_ERROR", true);
            fixture.신고("ANSWER_ERROR", false);
        }

        @Test
        void 필터가_없으면_전체를_id_내림차순으로_돌려준다() {
            // when
            List<ReportListItemResponse> page = reportRepository.findPage(null, null, 0, 20);

            // then
            assertSoftly(softly -> {
                softly.assertThat(page).extracting(ReportListItemResponse::reportId).containsExactly(4L, 3L, 2L, 1L);
                softly.assertThat(reportRepository.count(null, null)).isEqualTo(4);
            });
        }

        @Test
        void 유형과_처리_여부를_함께_거른다() {
            // when
            List<ReportListItemResponse> page = reportRepository.findPage(ReportType.TYPO_ERROR, true, 0, 20);

            // then
            assertSoftly(softly -> {
                softly.assertThat(page).extracting(ReportListItemResponse::reportId).containsExactly(3L);
                softly.assertThat(reportRepository.count(ReportType.TYPO_ERROR, true)).isEqualTo(1);
            });
        }

        @Test
        void 처리_여부만_거를_수_있다() {
            // when
            List<ReportListItemResponse> page = reportRepository.findPage(null, false, 0, 20);

            // then
            assertThat(page).extracting(ReportListItemResponse::reportId).containsExactly(4L, 1L);
        }

        @Test
        void offset과_limit으로_자른다() {
            // when
            List<ReportListItemResponse> page = reportRepository.findPage(null, null, 1, 2);

            // then
            assertThat(page).extracting(ReportListItemResponse::reportId).containsExactly(3L, 2L);
        }

        @Test
        void 목록_항목은_내용_없이_유형_문제_처리_여부_시각을_담는다() {
            // when
            ReportListItemResponse item = reportRepository.findPage(null, null, 0, 20).getLast();

            // then
            assertThat(item).isEqualTo(ReportListItemResponse.of(1L, ReportType.TYPO_ERROR, 301L, false, SUBMITTED_AT));
        }
    }

    @Nested
    @DisplayName("상세를 조회할 때")
    class Detail {

        @Test
        void 있으면_내용까지_돌려준다() {
            // given
            fixture.신고("OTHER_ERROR", true, "보기 2번 오타", SUBMITTED_AT);

            // when
            Optional<ReportDetailResponse> detail = reportRepository.findDetailById(1L);

            // then
            assertThat(detail).contains(ReportDetailResponse.of(1L, ReportType.OTHER_ERROR, 301L, "보기 2번 오타", true, SUBMITTED_AT));
        }

        @Test
        void 없으면_비어_있다() {
            // when & then
            assertThat(reportRepository.findDetailById(999L)).isEmpty();
        }
    }
}
