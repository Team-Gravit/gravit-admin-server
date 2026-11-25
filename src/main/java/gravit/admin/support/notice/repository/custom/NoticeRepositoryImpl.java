package gravit.admin.support.notice.repository.custom;

import gravit.admin.global.annotation.MainDb;
import gravit.admin.support.notice.domain.NoticeStatus;
import gravit.admin.support.notice.dto.response.NoticeDetailResponse;
import gravit.admin.support.notice.dto.response.NoticeListItemResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static gravit.admin.support.notice.repository.sql.NoticeSql.COUNT_NOTICES;
import static gravit.admin.support.notice.repository.sql.NoticeSql.SELECT_NOTICE_DETAIL;
import static gravit.admin.support.notice.repository.sql.NoticeSql.SELECT_NOTICE_PAGE;

@Repository
public class NoticeRepositoryImpl implements NoticeRepository {

    private static final RowMapper<NoticeListItemResponse> LIST_ITEM_ROW_MAPPER = (rs, rowNum) -> NoticeListItemResponse.of(
            rs.getLong("id"),
            rs.getString("title"),
            NoticeStatus.valueOf(rs.getString("status")),
            rs.getBoolean("pinned"),
            rs.getObject("published_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private static final RowMapper<NoticeDetailResponse> DETAIL_ROW_MAPPER = (rs, rowNum) -> NoticeDetailResponse.of(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("summary"),
            rs.getString("content"),
            NoticeStatus.valueOf(rs.getString("status")),
            rs.getBoolean("pinned"),
            rs.getObject("published_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private final NamedParameterJdbcTemplate mainJdbcTemplate;

    public NoticeRepositoryImpl(@MainDb JdbcTemplate mainJdbcTemplate) {
        this.mainJdbcTemplate = new NamedParameterJdbcTemplate(mainJdbcTemplate);
    }

    @Override
    public List<NoticeListItemResponse> findPage(
            long offset,
            int limit
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
        return mainJdbcTemplate.query(SELECT_NOTICE_PAGE, params, LIST_ITEM_ROW_MAPPER);
    }

    @Override
    public long count() {
        Long count = mainJdbcTemplate.queryForObject(COUNT_NOTICES, new MapSqlParameterSource(), Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<NoticeDetailResponse> findDetailById(long noticeId) {
        MapSqlParameterSource params = new MapSqlParameterSource("noticeId", noticeId);
        return mainJdbcTemplate.query(SELECT_NOTICE_DETAIL, params, DETAIL_ROW_MAPPER).stream().findFirst();
    }
}
