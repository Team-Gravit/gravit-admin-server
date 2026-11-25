package gravit.admin.insight.service;

import gravit.admin.insight.dto.response.DailyActiveUserPointResponse;
import gravit.admin.insight.dto.response.DailyActiveUserTrendResponse;
import gravit.admin.insight.dto.response.DashboardSummaryResponse;
import gravit.admin.insight.dto.response.MonthlyActiveUserPointResponse;
import gravit.admin.insight.dto.response.MonthlyActiveUserTrendResponse;
import gravit.admin.insight.fixture.DashboardMainDbFixture;
import gravit.admin.support.QaSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시계는 TestClock 기준 2026-10-16 (KST)이다.
 */
@QaSpringBootTest
@Import(DashboardMainDbFixture.class)
class DashboardServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private DashboardMainDbFixture fixture;

    @Test
    void 요약은_DELETED를_뺀_회원_대기_라벨_미처리_신고를_센다() {
        // given
        fixture.회원("ACTIVE");
        fixture.회원("SUSPENDED");
        fixture.회원("DELETED");
        fixture.스테이징_라벨("PENDING");
        fixture.스테이징_라벨("COMPLETED");
        fixture.신고(false);
        fixture.신고(false);
        fixture.신고(true);

        // when
        DashboardSummaryResponse summary = dashboardService.getSummary();

        // then
        assertThat(summary).isEqualTo(DashboardSummaryResponse.of(2, 1, 2));
    }

    @Nested
    @DisplayName("일별 활성 유저 추이를 조회할 때")
    class Daily {

        @Test
        void 요청_일수만큼_연속된_포인트를_오래된_날짜부터_돌려주고_활동이_없는_날은_0이다() {
            // given
            fixture.활동(1L, TODAY.minusDays(2));
            fixture.활동(2L, TODAY.minusDays(2));
            fixture.활동(1L, TODAY);

            // when
            DailyActiveUserTrendResponse response = dashboardService.getDailyActiveUsers(4);

            // then
            assertThat(response.points()).containsExactly(
                    DailyActiveUserPointResponse.of(TODAY.minusDays(3), 0),
                    DailyActiveUserPointResponse.of(TODAY.minusDays(2), 2),
                    DailyActiveUserPointResponse.of(TODAY.minusDays(1), 0),
                    DailyActiveUserPointResponse.of(TODAY, 1)
            );
        }

        @Test
        void 조회_구간_시작일_활동은_포함하고_그_전날_활동은_제외한다() {
            // given
            fixture.활동(1L, TODAY.minusDays(2));
            fixture.활동(2L, TODAY.minusDays(3));

            // when
            DailyActiveUserTrendResponse response = dashboardService.getDailyActiveUsers(3);

            // then
            assertThat(response.points().getFirst()).isEqualTo(DailyActiveUserPointResponse.of(TODAY.minusDays(2), 1));
            assertThat(response.points()).extracting(DailyActiveUserPointResponse::dau).containsExactly(1L, 0L, 0L);
        }

        @Test
        void 하루만_조회하면_오늘_포인트_하나다() {
            // when
            DailyActiveUserTrendResponse response = dashboardService.getDailyActiveUsers(1);

            // then
            assertThat(response.points()).containsExactly(DailyActiveUserPointResponse.of(TODAY, 0));
        }

        @Test
        void 활동_이력이_전혀_없으면_모든_포인트가_0이다() {
            // when
            DailyActiveUserTrendResponse response = dashboardService.getDailyActiveUsers(30);

            // then
            assertThat(response.points()).hasSize(30).allSatisfy(point -> assertThat(point.dau()).isZero());
        }
    }

    @Nested
    @DisplayName("월별 활성 유저 추이를 조회할 때")
    class Monthly {

        @Test
        void 요청_개월_수만큼_연속된_포인트를_오래된_달부터_돌려주고_활동이_없는_달은_0이다() {
            // given
            fixture.활동(1L, LocalDate.of(2026, 8, 3));
            fixture.활동(1L, LocalDate.of(2026, 10, 1));

            // when
            MonthlyActiveUserTrendResponse response = dashboardService.getMonthlyActiveUsers(3);

            // then
            assertThat(response.points()).containsExactly(
                    MonthlyActiveUserPointResponse.of("2026-08", 1, false),
                    MonthlyActiveUserPointResponse.of("2026-09", 0, false),
                    MonthlyActiveUserPointResponse.of("2026-10", 1, true)
            );
        }

        @Test
        void 한_유저가_한_달에_여러_날_활동해도_그_달에_한_명으로_센다() {
            // given
            fixture.활동(1L, LocalDate.of(2026, 10, 1));
            fixture.활동(1L, LocalDate.of(2026, 10, 2));
            fixture.활동(2L, LocalDate.of(2026, 10, 2));

            // when
            MonthlyActiveUserTrendResponse response = dashboardService.getMonthlyActiveUsers(1);

            // then
            assertThat(response.points()).containsExactly(MonthlyActiveUserPointResponse.of("2026-10", 2, true));
        }

        @Test
        void 해가_바뀌는_구간도_연속으로_돌려준다() {
            // given
            fixture.활동(1L, LocalDate.of(2025, 12, 31));

            // when
            MonthlyActiveUserTrendResponse response = dashboardService.getMonthlyActiveUsers(11);

            // then
            assertThat(response.points().getFirst()).isEqualTo(MonthlyActiveUserPointResponse.of("2025-12", 1, false));
            assertThat(response.points()).extracting(MonthlyActiveUserPointResponse::month).hasSize(11).endsWith("2026-10");
        }

        @Test
        void 조회_구간_시작_달의_첫날_활동은_포함하고_그_전날_활동은_제외한다() {
            // given
            fixture.활동(1L, LocalDate.of(2026, 9, 1));
            fixture.활동(2L, LocalDate.of(2026, 8, 31));

            // when
            MonthlyActiveUserTrendResponse response = dashboardService.getMonthlyActiveUsers(2);

            // then
            assertThat(response.points()).containsExactly(
                    MonthlyActiveUserPointResponse.of("2026-09", 1, false),
                    MonthlyActiveUserPointResponse.of("2026-10", 0, true)
            );
        }
    }
}
