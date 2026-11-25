package gravit.admin.support;

import gravit.admin.adapter.llm.support.LlmCallGuard;
import gravit.admin.qa.autoReview.support.RepoSourceReader;
import gravit.admin.auth.support.AdminVerificationCache;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

public class DatabaseClearExtension implements BeforeEachCallback {

    private static final String SELECT_TABLES = """
            SELECT quote_ident(schemaname) || '.' || quote_ident(tablename)
            FROM pg_tables
            WHERE (schemaname = 'public' AND tablename <> 'flyway_schema_history')
               OR schemaname = ?
            """;

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        clearTables(applicationContext.getBean(JdbcTemplate.class));

        applicationContext.getBean(TestClock.class).setInstant(TestClockConfig.BASE_INSTANT);
        applicationContext.getBean(AdminMeStubServer.class).reset();
        applicationContext.getBean(AdminVerificationCache.class).clear();
        applicationContext.getBean(HttpStubServer.class).reset();
        applicationContext.getBean(RecordingSleeper.class).reset();
        applicationContext.getBean(LlmCallGuard.class).clear();
        applicationContext.getBean(RepoSourceReader.class).clear();
    }

    /**
     * 관리자 DB(public)와 메인 DB 픽스처 스키마의 행을 비운다. 테이블 구조는 그대로 둔다.
     */
    public static void clearTables(JdbcTemplate adminJdbcTemplate) {
        List<String> tables = adminJdbcTemplate.queryForList(SELECT_TABLES, String.class, TestDatabase.MAIN_DB_SCHEMA);
        if (!tables.isEmpty()) {
            adminJdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }
}
