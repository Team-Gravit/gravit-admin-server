package gravit.admin.insight.repository.sql;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DashboardStatsSql {

    public static final String COUNT_ACTIVE_USERS = """
            SELECT COUNT(*)
            FROM users
            WHERE status <> 'DELETED'
            """;

    public static final String COUNT_PENDING_LABELS = """
            SELECT COUNT(*)
            FROM staging_label
            WHERE status = 'PENDING'
            """;

    public static final String COUNT_UNRESOLVED_REPORTS = """
            SELECT COUNT(*)
            FROM report
            WHERE is_resolved = false
            """;

    public static final String SELECT_DAILY_ACTIVE_USER_COUNTS = """
            SELECT activity_date, COUNT(user_id) AS active_user_count
            FROM user_daily_activity
            WHERE activity_date BETWEEN ? AND ?
            GROUP BY activity_date
            ORDER BY activity_date
            """;

    public static final String SELECT_MONTHLY_ACTIVE_USER_COUNTS = """
            SELECT CAST(EXTRACT(YEAR FROM activity_date) AS INTEGER) AS activity_year,
                   CAST(EXTRACT(MONTH FROM activity_date) AS INTEGER) AS activity_month,
                   COUNT(DISTINCT user_id) AS active_user_count
            FROM user_daily_activity
            WHERE activity_date BETWEEN ? AND ?
            GROUP BY activity_year, activity_month
            ORDER BY activity_year, activity_month
            """;
}
