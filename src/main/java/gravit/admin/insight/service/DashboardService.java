package gravit.admin.insight.service;

import gravit.admin.insight.dto.internal.DailyActiveUserCountDto;
import gravit.admin.insight.dto.internal.MonthlyActiveUserCountDto;
import gravit.admin.insight.dto.response.DailyActiveUserPointResponse;
import gravit.admin.insight.dto.response.DailyActiveUserTrendResponse;
import gravit.admin.insight.dto.response.DashboardSummaryResponse;
import gravit.admin.insight.dto.response.MonthlyActiveUserPointResponse;
import gravit.admin.insight.dto.response.MonthlyActiveUserTrendResponse;
import gravit.admin.insight.repository.custom.DashboardStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DashboardStatsRepository dashboardStatsRepository;

    private final Clock clock;

    public DashboardSummaryResponse getSummary() {
        long totalUsers = dashboardStatsRepository.countActiveUsers();
        long pendingLabelsCount = dashboardStatsRepository.countPendingLabels();
        long unresolvedReportsCount = dashboardStatsRepository.countUnresolvedReports();

        return DashboardSummaryResponse.of(totalUsers, pendingLabelsCount, unresolvedReportsCount);
    }

    public DailyActiveUserTrendResponse getDailyActiveUsers(int days) {
        LocalDate endDate = LocalDate.now(clock);
        LocalDate startDate = endDate.minusDays(days - 1L);

        Map<LocalDate, Long> dateToActiveUserCount = dashboardStatsRepository
                .findDailyActiveUserCounts(startDate, endDate).stream()
                .collect(Collectors.toMap(DailyActiveUserCountDto::activityDate, DailyActiveUserCountDto::activeUserCount));

        List<DailyActiveUserPointResponse> points = startDate.datesUntil(endDate.plusDays(1))
                .map(date -> DailyActiveUserPointResponse.of(date, dateToActiveUserCount.getOrDefault(date, 0L)))
                .toList();

        return DailyActiveUserTrendResponse.of(points);
    }

    public MonthlyActiveUserTrendResponse getMonthlyActiveUsers(int months) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth startMonth = currentMonth.minusMonths(months - 1L);

        Map<YearMonth, Long> monthToActiveUserCount = dashboardStatsRepository
                .findMonthlyActiveUserCounts(startMonth.atDay(1), today).stream()
                .collect(Collectors.toMap(
                        dto -> YearMonth.of(dto.year(), dto.month()),
                        MonthlyActiveUserCountDto::activeUserCount
                ));

        List<MonthlyActiveUserPointResponse> points = Stream.iterate(startMonth, month -> month.plusMonths(1))
                .limit(months)
                .map(month -> MonthlyActiveUserPointResponse.of(
                        month.format(MONTH_FORMATTER),
                        monthToActiveUserCount.getOrDefault(month, 0L),
                        month.equals(currentMonth)
                ))
                .toList();

        return MonthlyActiveUserTrendResponse.of(points);
    }
}
