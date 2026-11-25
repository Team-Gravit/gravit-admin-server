package gravit.admin.support;

import gravit.admin.global.annotation.MainDb;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class MainDbFixtureIntegrationTest {

    private static final String PROBE_SCHEMA = "maindb_probe";
    private static final String PROBE_LOCATION = "classpath*:maindb-probe/*.sql";
    private static final String FIXTURE_TABLE = "fixture_probe";

    @Autowired
    private MainDbFixtureWriter mainDbFixtureWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MainDb
    @Autowired
    private JdbcTemplate mainJdbcTemplate;

    @Nested
    @DisplayName("픽스처 DDL 파일을 깔 때")
    class Prepare {

        @AfterEach
        void tearDown() {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + PROBE_SCHEMA + " CASCADE");
        }

        @Test
        void 파일_이름_순서로_실행해_스키마_안에_테이블을_만든다() {
            // given
            DataSource probeDataSource = TestDatabase.get().writableDataSource(PROBE_SCHEMA);

            // when
            List<String> executed = MainDbFixtureSchema.prepare(probeDataSource, PROBE_SCHEMA, PROBE_LOCATION);

            // then
            assertSoftly(softly -> {
                softly.assertThat(executed).containsExactly("V01__probe_parent.sql", "V02__probe_child.sql");
                softly.assertThat(jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, PROBE_SCHEMA + ".probe_child"))
                        .isEqualTo(PROBE_SCHEMA + ".probe_child");
                softly.assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('probe_child')::text", String.class)).isNull();
            });
        }

        @Test
        void 다시_깔면_스키마를_비우고_새로_만든다() {
            // given
            DataSource probeDataSource = TestDatabase.get().writableDataSource(PROBE_SCHEMA);
            MainDbFixtureSchema.prepare(probeDataSource, PROBE_SCHEMA, PROBE_LOCATION);
            new JdbcTemplate(probeDataSource).update("INSERT INTO probe_parent (id, name) VALUES (1, 'old')");

            // when
            MainDbFixtureSchema.prepare(probeDataSource, PROBE_SCHEMA, PROBE_LOCATION);

            // then
            assertThat(new JdbcTemplate(probeDataSource).queryForObject("SELECT count(*) FROM probe_parent", Integer.class)).isZero();
        }
    }

    @Nested
    @DisplayName("테스트에서 메인 DB 행을 다룰 때")
    class Rows {

        @BeforeEach
        void setUp() {
            mainDbFixtureWriter.execute("CREATE TABLE " + FIXTURE_TABLE + " (id BIGINT PRIMARY KEY, name VARCHAR(50) NOT NULL)");
        }

        @AfterEach
        void tearDown() {
            mainDbFixtureWriter.execute("DROP TABLE IF EXISTS " + FIXTURE_TABLE);
        }

        @Test
        void 메인_DB_연결은_픽스처_스키마를_본다() {
            // when
            String schema = mainJdbcTemplate.queryForObject("SELECT current_schema()", String.class);

            // then
            assertThat(schema).isEqualTo(TestDatabase.MAIN_DB_SCHEMA);
        }

        @Test
        void 넣은_행은_메인_DB_연결로_읽히고_관리자_DB_연결에서는_보이지_않는다() {
            // given
            mainDbFixtureWriter.update("INSERT INTO " + FIXTURE_TABLE + " (id, name) VALUES (?, ?)", 1L, "main-row");

            // when
            String name = mainJdbcTemplate.queryForObject("SELECT name FROM " + FIXTURE_TABLE + " WHERE id = 1", String.class);
            String adminTable = jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, FIXTURE_TABLE);

            // then
            assertSoftly(softly -> {
                softly.assertThat(name).isEqualTo("main-row");
                softly.assertThat(adminTable).isNull();
            });
        }

        @Test
        void 테스트_사이_정리에서_행만_비우고_테이블은_남긴다() {
            // given
            mainDbFixtureWriter.update("INSERT INTO " + FIXTURE_TABLE + " (id, name) VALUES (?, ?)", 1L, "main-row");

            // when
            DatabaseClearExtension.clearTables(jdbcTemplate);

            // then
            assertThat(mainJdbcTemplate.queryForObject("SELECT count(*) FROM " + FIXTURE_TABLE, Integer.class)).isZero();
        }
    }
}
