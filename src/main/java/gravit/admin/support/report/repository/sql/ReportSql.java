package gravit.admin.support.report.repository.sql;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ReportSql {

    public static final String SELECT_REPORT_PAGE = """
            SELECT id, report_type, problem_id, is_resolved, submitted_at
            FROM report
            WHERE (CAST(:reportType AS VARCHAR) IS NULL OR report_type = :reportType)
              AND (CAST(:isResolved AS BOOLEAN) IS NULL OR is_resolved = :isResolved)
            ORDER BY id DESC
            LIMIT :limit OFFSET :offset
            """;

    public static final String COUNT_REPORTS = """
            SELECT COUNT(*)
            FROM report
            WHERE (CAST(:reportType AS VARCHAR) IS NULL OR report_type = :reportType)
              AND (CAST(:isResolved AS BOOLEAN) IS NULL OR is_resolved = :isResolved)
            """;

    public static final String SELECT_REPORT_DETAIL = """
            SELECT id, report_type, problem_id, content, is_resolved, submitted_at
            FROM report
            WHERE id = :reportId
            """;
}
