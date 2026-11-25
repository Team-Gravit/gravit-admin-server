package gravit.admin.insight.repository.custom;

import gravit.admin.global.annotation.MainDb;
import gravit.admin.insight.dto.internal.DailyActiveUserCountDto;
import gravit.admin.insight.dto.internal.MonthlyActiveUserCountDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static gravit.admin.insight.repository.sql.DashboardStatsSql.COUNT_ACTIVE_USERS;
import static gravit.admin.insight.repository.sql.DashboardStatsSql.COUNT_PENDING_LABELS;
import static gravit.admin.insight.repository.sql.DashboardStatsSql.COUNT_UNRESOLVED_REPORTS;
import static gravit.admin.insight.repository.sql.DashboardStatsSql.SELECT_DAILY_ACTIVE_USER_COUNTS;
import static gravit.admin.insight.repository.sql.DashboardStatsSql.SELECT_MONTHLY_ACTIVE_USER_COUNTS;

@Repository
@RequiredArgsConstructor
public class DashboardStatsRepositoryImpl implements DashboardStatsRepository {

    private static final RowMapper<DailyActiveUserCountDto> DAILY_ROW_MAPPER = (rs, rowNum) -> new DailyActiveUserCountDto(
            rs.getObject("activity_date", LocalDate.class),
            rs.getLong("active_user_count")
    );

    private static final RowMapper<MonthlyActiveUserCountDto> MONTHLY_ROW_MAPPER = (rs, rowNum) -> new MonthlyActiveUserCountDto(
            rs.getInt("activity_year"),
            rs.getInt("activity_month"),
            rs.getLong("active_user_count")
    );

    @MainDb
    private final JdbcTemplate mainJdbcTemplate;

    @Override
    public long countActiveUsers() {
        return count(COUNT_ACTIVE_USERS);
    }

    @Override
    public long countPendingLabels() {
        return count(COUNT_PENDING_LABELS);
    }

    @Override
    public long countUnresolvedReports() {
        return count(COUNT_UNRESOLVED_REPORTS);
    }

    @Override
    public List<DailyActiveUserCountDto> findDailyActiveUserCounts(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return mainJdbcTemplate.query(SELECT_DAILY_ACTIVE_USER_COUNTS, DAILY_ROW_MAPPER, startDate, endDate);
    }

    @Override
    public List<MonthlyActiveUserCountDto> findMonthlyActiveUserCounts(
            LocalDate startDate,
            LocalDate endDate
    ) {
        return mainJdbcTemplate.query(SELECT_MONTHLY_ACTIVE_USER_COUNTS, MONTHLY_ROW_MAPPER, startDate, endDate);
    }

    private long count(String sql) {
        Long count = mainJdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
