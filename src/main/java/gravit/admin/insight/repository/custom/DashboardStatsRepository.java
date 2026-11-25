package gravit.admin.insight.repository.custom;

import gravit.admin.insight.dto.internal.DailyActiveUserCountDto;
import gravit.admin.insight.dto.internal.MonthlyActiveUserCountDto;

import java.time.LocalDate;
import java.util.List;

public interface DashboardStatsRepository {

    long countActiveUsers();

    long countPendingLabels();

    long countUnresolvedReports();

    List<DailyActiveUserCountDto> findDailyActiveUserCounts(
            LocalDate startDate,
            LocalDate endDate
    );

    List<MonthlyActiveUserCountDto> findMonthlyActiveUserCounts(
            LocalDate startDate,
            LocalDate endDate
    );
}
