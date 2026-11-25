package gravit.admin.support.report.repository.custom;

import gravit.admin.global.annotation.MainDb;
import gravit.admin.support.report.domain.ReportType;
import gravit.admin.support.report.dto.response.ReportDetailResponse;
import gravit.admin.support.report.dto.response.ReportListItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static gravit.admin.support.report.repository.sql.ReportSql.COUNT_REPORTS;
import static gravit.admin.support.report.repository.sql.ReportSql.SELECT_REPORT_DETAIL;
import static gravit.admin.support.report.repository.sql.ReportSql.SELECT_REPORT_PAGE;

@Repository
public class ReportRepositoryImpl implements ReportRepository {

    private static final RowMapper<ReportListItemResponse> LIST_ITEM_ROW_MAPPER = (rs, rowNum) -> ReportListItemResponse.of(
            rs.getLong("id"),
            ReportType.valueOf(rs.getString("report_type")),
            rs.getLong("problem_id"),
            rs.getBoolean("is_resolved"),
            rs.getObject("submitted_at", LocalDateTime.class)
    );

    private static final RowMapper<ReportDetailResponse> DETAIL_ROW_MAPPER = (rs, rowNum) -> ReportDetailResponse.of(
            rs.getLong("id"),
            ReportType.valueOf(rs.getString("report_type")),
            rs.getLong("problem_id"),
            rs.getString("content"),
            rs.getBoolean("is_resolved"),
            rs.getObject("submitted_at", LocalDateTime.class)
    );

    private final NamedParameterJdbcTemplate mainJdbcTemplate;

    public ReportRepositoryImpl(@MainDb JdbcTemplate mainJdbcTemplate) {
        this.mainJdbcTemplate = new NamedParameterJdbcTemplate(mainJdbcTemplate);
    }

    @Override
    public List<ReportListItemResponse> findPage(
            ReportType reportType,
            Boolean isResolved,
            long offset,
            int limit
    ) {
        MapSqlParameterSource params = filterParams(reportType, isResolved)
                .addValue("offset", offset)
                .addValue("limit", limit);
        return mainJdbcTemplate.query(SELECT_REPORT_PAGE, params, LIST_ITEM_ROW_MAPPER);
    }

    @Override
    public long count(
            ReportType reportType,
            Boolean isResolved
    ) {
        Long count = mainJdbcTemplate.queryForObject(COUNT_REPORTS, filterParams(reportType, isResolved), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<ReportDetailResponse> findDetailById(long reportId) {
        MapSqlParameterSource params = new MapSqlParameterSource("reportId", reportId);
        return mainJdbcTemplate.query(SELECT_REPORT_DETAIL, params, DETAIL_ROW_MAPPER).stream().findFirst();
    }

    private MapSqlParameterSource filterParams(
            ReportType reportType,
            Boolean isResolved
    ) {
        return new MapSqlParameterSource()
                .addValue("reportType", reportType == null ? null : reportType.name())
                .addValue("isResolved", isResolved);
    }
}
