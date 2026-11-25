package gravit.admin.insight.fixture;

import gravit.admin.support.MainDbFixtureWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 대시보드가 읽는 메인 테이블(users, staging_label, report, user_daily_activity)에 행을 넣는다.
 */
@TestComponent
@RequiredArgsConstructor
public class DashboardMainDbFixture {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final AtomicLong sequence = new AtomicLong();

    private final MainDbFixtureWriter mainDbFixtureWriter;

    public void 회원(String status) {
        long id = sequence.incrementAndGet();
        mainDbFixtureWriter.update("""
                INSERT INTO users (is_onboarded, level, profile_img_number, xp, created_at, email, handle, nickname,
                                   provider_id, role, status)
                VALUES (true, 1, 1, 0, ?, ?, ?, ?, ?, 'USER', ?)
                """, CREATED_AT, "user" + id + "@gravit.test", "handle" + id, "닉네임" + id, "provider-" + id, status);
    }

    public void 스테이징_라벨(String status) {
        long id = sequence.incrementAndGet();
        mainDbFixtureWriter.update("""
                INSERT INTO staging_label (id, label, unit_id, description, status)
                VALUES (?, ?, 1, '라벨 설명', ?)
                """, id, "label-" + id, status);
    }

    public void 신고(boolean resolved) {
        mainDbFixtureWriter.update("""
                INSERT INTO report (is_resolved, problem_id, submitted_at, user_id, content, report_type)
                VALUES (?, 1, ?, 1, '신고 내용', 'TYPO_ERROR')
                """, resolved, CREATED_AT);
    }

    public void 활동(
            long userId,
            LocalDate activityDate
    ) {
        mainDbFixtureWriter.update("""
                INSERT INTO user_daily_activity (user_id, activity_date)
                VALUES (?, ?)
                """, userId, activityDate);
    }
}
