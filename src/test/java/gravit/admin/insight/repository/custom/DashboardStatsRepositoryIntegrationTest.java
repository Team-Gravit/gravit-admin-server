package gravit.admin.insight.repository.custom;

import gravit.admin.insight.dto.internal.DailyActiveUserCountDto;
import gravit.admin.insight.dto.internal.MonthlyActiveUserCountDto;
import gravit.admin.insight.fixture.DashboardMainDbFixture;
import gravit.admin.support.QaSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
@Import(DashboardMainDbFixture.class)
class DashboardStatsRepositoryIntegrationTest {

    @Autowired
    private DashboardStatsRepository dashboardStatsRepository;

    @Autowired
    private DashboardMainDbFixture fixture;

    @Nested
    @DisplayName("요약 수치를 셀 때")
    class Counts {

        @Test
        void 회원은_DELETED만_빼고_정지_회원은_센다() {
            // given
            fixture.회원("ACTIVE");
            fixture.회원("SUSPENDED");
            fixture.회원("DELETED");

            // when
            long count = dashboardStatsRepository.countActiveUsers();

            // then
            assertThat(count).isEqualTo(2);
        }

        @Test
        void 라벨은_PENDING만_센다() {
            // given
            fixture.스테이징_라벨("PENDING");
            fixture.스테이징_라벨("PENDING");
            fixture.스테이징_라벨("COMPLETED");

            // when
            long count = dashboardStatsRepository.countPendingLabels();

            // then
            assertThat(count).isEqualTo(2);
        }

        @Test
        void 신고는_처리하지_않은_것만_센다() {
            // given
            fixture.신고(false);
            fixture.신고(true);

            // when
            long count = dashboardStatsRepository.countUnresolvedReports();

            // then
            assertThat(count).isEqualTo(1);
        }

        @Test
        void 행이_없으면_0이다() {
            // when & then
            assertSoftly(softly -> {
                softly.assertThat(dashboardStatsRepository.countActiveUsers()).isZero();
                softly.assertThat(dashboardStatsRepository.countPendingLabels()).isZero();
                softly.assertThat(dashboardStatsRepository.countUnresolvedReports()).isZero();
            });
        }
    }

    @Nested
    @DisplayName("활성 회원 수를 기간으로 셀 때")
    class ActiveUsers {

        @Test
        void 일별은_구간_양끝을_포함해_날짜순으로_센다() {
            // given
            fixture.활동(1L, LocalDate.of(2026, 10, 9));
            fixture.활동(1L, LocalDate.of(2026, 10, 10));
            fixture.활동(2L, LocalDate.of(2026, 10, 10));
            fixture.활동(1L, LocalDate.of(2026, 10, 16));
            fixture.활동(1L, LocalDate.of(2026, 10, 17));

            // when
            List<DailyActiveUserCountDto> counts = dashboardStatsRepository.findDailyActiveUserCounts(
                    LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 16));

            // then
            assertThat(counts).containsExactly(
                    new DailyActiveUserCountDto(LocalDate.of(2026, 10, 10), 2),
                    new DailyActiveUserCountDto(LocalDate.of(2026, 10, 16), 1)
            );
        }

        @Test
        void 월별은_한_유저를_한_달에_한_번만_센다() {
            // given
            fixture.활동(1L, LocalDate.of(2026, 9, 1));
            fixture.활동(1L, LocalDate.of(2026, 9, 2));
            fixture.활동(2L, LocalDate.of(2026, 9, 30));
            fixture.활동(1L, LocalDate.of(2026, 10, 16));

            // when
            List<MonthlyActiveUserCountDto> counts = dashboardStatsRepository.findMonthlyActiveUserCounts(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 16));

            // then
            assertThat(counts).containsExactly(
                    new MonthlyActiveUserCountDto(2026, 9, 2),
                    new MonthlyActiveUserCountDto(2026, 10, 1)
            );
        }
    }
}
