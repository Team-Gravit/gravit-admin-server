package gravit.admin.support.notice.repository.sql;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NoticeSql {

    public static final String SELECT_NOTICE_PAGE = """
            SELECT id, title, status, pinned, published_at, created_at
            FROM notice
            WHERE deleted_at IS NULL
            ORDER BY pinned DESC, published_at DESC NULLS LAST, created_at DESC
            LIMIT :limit OFFSET :offset
            """;

    public static final String COUNT_NOTICES = """
            SELECT COUNT(*)
            FROM notice
            WHERE deleted_at IS NULL
            """;

    public static final String SELECT_NOTICE_DETAIL = """
            SELECT id, title, summary, content, status, pinned, published_at, created_at
            FROM notice
            WHERE id = :noticeId AND deleted_at IS NULL
            """;
}
