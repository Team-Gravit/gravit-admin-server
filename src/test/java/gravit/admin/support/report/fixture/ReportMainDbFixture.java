package gravit.admin.support.report.fixture;

import gravit.admin.support.MainDbFixtureWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.time.LocalDateTime;

/**
 * 메인 report 테이블에 행을 넣는다. id는 IDENTITY라 넣은 순서대로 1부터 붙는다(테스트마다 RESTART IDENTITY).
 */
@TestComponent
@RequiredArgsConstructor
public class ReportMainDbFixture {

    public static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 11, 18, 10, 15, 30, 123_456_000);

    private final MainDbFixtureWriter mainDbFixtureWriter;

    public void 신고(
            String reportType,
            boolean resolved
    ) {
        신고(reportType, resolved, "신고 내용", SUBMITTED_AT);
    }

    public void 신고(
            String reportType,
            boolean resolved,
            String content,
            LocalDateTime submittedAt
    ) {
        mainDbFixtureWriter.update("""
                INSERT INTO report (is_resolved, problem_id, submitted_at, user_id, content, report_type)
                VALUES (?, 301, ?, 7, ?, ?)
                """, resolved, submittedAt, content, reportType);
    }

    public void 신고_여러_건(int count) {
        for (int i = 0; i < count; i++) {
            신고("TYPO_ERROR", false);
        }
    }
}
