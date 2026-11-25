package gravit.admin.support.notice.fixture;

import gravit.admin.support.MainDbFixtureWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.time.LocalDateTime;

/**
 * 메인 notice 테이블에 행을 넣는다. id는 넣은 순서대로 1부터 붙는다(테스트마다 RESTART IDENTITY).
 */
@TestComponent
@RequiredArgsConstructor
public class NoticeMainDbFixture {

    public static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 1, 9, 0);

    private final MainDbFixtureWriter mainDbFixtureWriter;

    public void 게시된_공지(
            String title,
            boolean pinned,
            LocalDateTime publishedAt
    ) {
        공지(title, "PUBLISHED", pinned, publishedAt, publishedAt, null);
    }

    public void 임시저장_공지(
            String title,
            LocalDateTime createdAt
    ) {
        공지(title, "DRAFT", false, null, createdAt, null);
    }

    public void 삭제된_공지(String title) {
        공지(title, "PUBLISHED", false, BASE_TIME, BASE_TIME, BASE_TIME.plusDays(1));
    }

    public void 공지(
            String title,
            String status,
            boolean pinned,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime deletedAt
    ) {
        mainDbFixtureWriter.update("""
                INSERT INTO notice (pinned, author_id, created_at, published_at, updated_at, content, status, title, summary, deleted_at)
                VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?)
                """, pinned, createdAt, publishedAt, createdAt, title + " 본문", status, title, title + " 요약", deletedAt);
    }
}
