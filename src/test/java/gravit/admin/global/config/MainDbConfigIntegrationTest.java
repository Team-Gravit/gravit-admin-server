package gravit.admin.global.config;

import com.zaxxer.hikari.HikariDataSource;
import gravit.admin.global.annotation.MainDb;
import gravit.admin.support.QaSpringBootTest;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
class MainDbConfigIntegrationTest {

    private static final String MAIN_POOL_NAME = "main-db";
    private static final String WRITE_PROBE_TABLE = "main_db_write_probe";
    private static final String READ_ONLY_SETTING = "SHOW transaction_read_only";
    private static final String UNREACHABLE_URL = "jdbc:postgresql://localhost:1/unreachable";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MainDb
    @Autowired
    private DataSource mainDataSource;

    @MainDb
    @Autowired
    private JdbcTemplate mainJdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Flyway flyway;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Nested
    @DisplayName("관리자 DB와 메인 DB 연결을 나눌 때")
    class Separation {

        @Test
        void 한정자_없는_DataSource_JdbcTemplate_JPA_Flyway는_관리자_DB_풀을_쓴다() {
            // when & then
            assertSoftly(softly -> {
                softly.assertThat(dataSource).isNotSameAs(mainDataSource);
                softly.assertThat(((HikariDataSource) dataSource).getPoolName()).isNotEqualTo(MAIN_POOL_NAME);
                softly.assertThat(jdbcTemplate.getDataSource()).isSameAs(dataSource);
                softly.assertThat(((EntityManagerFactoryInfo) entityManagerFactory).getDataSource()).isSameAs(dataSource);
                softly.assertThat(flyway.getConfiguration().getDataSource()).isSameAs(dataSource);
            });
        }

        @Test
        void 관리자_DB_풀은_최대_5개다() {
            // when & then
            assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(5);
        }

        @Test
        void 메인_DB_JdbcTemplate은_최대_3개_최소_1개의_읽기_전용_풀을_쓴다() {
            // given
            HikariDataSource main = (HikariDataSource) mainDataSource;

            // when & then
            assertSoftly(softly -> {
                softly.assertThat(mainJdbcTemplate.getDataSource()).isSameAs(mainDataSource);
                softly.assertThat(main.getPoolName()).isEqualTo(MAIN_POOL_NAME);
                softly.assertThat(main.getMaximumPoolSize()).isEqualTo(3);
                softly.assertThat(main.getMinimumIdle()).isEqualTo(1);
                softly.assertThat(main.isReadOnly()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("메인 DB 연결로 SQL을 보낼 때")
    class ReadOnly {

        @Test
        void 조회는_되고_연결은_읽기_전용이다() {
            // when
            String mainReadOnly = mainJdbcTemplate.queryForObject(READ_ONLY_SETTING, String.class);
            String adminReadOnly = jdbcTemplate.queryForObject(READ_ONLY_SETTING, String.class);

            // then
            assertSoftly(softly -> {
                softly.assertThat(mainReadOnly).isEqualTo("on");
                softly.assertThat(adminReadOnly).isEqualTo("off");
            });
        }

        @Test
        void 자동_커밋_쓰기도_읽기_전용이라_실패한다() {
            // when & then
            assertThatThrownBy(() -> mainJdbcTemplate.execute("CREATE TABLE " + WRITE_PROBE_TABLE + " (id int)"))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("read-only transaction");

            assertThat(jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, WRITE_PROBE_TABLE)).isNull();
        }
    }

    @Nested
    @DisplayName("health를 확인할 때")
    class HealthCheck {

        @Test
        void DB_health는_관리자_DB_하나만_본다() {
            // when
            HealthComponent db = healthEndpoint.healthForPath("db");

            // then
            assertSoftly(softly -> {
                softly.assertThat(db).isInstanceOf(Health.class);
                softly.assertThat(db.getStatus()).isEqualTo(Status.UP);
            });
        }
    }

    @Nested
    @DisplayName("메인 DB에 닿지 못할 때")
    class Unreachable {

        @Test
        void 풀을_만들어도_연결하지_않고_첫_조회에서_실패한다() {
            // given
            MainDbConfig mainDbConfig = new MainDbConfig();
            MainDbProps props = new MainDbProps(UNREACHABLE_URL, "gravit_admin_ro", "", 3, 1);

            // when
            try (HikariDataSource unreachable = mainDbConfig.mainDataSource(props)) {
                boolean runningBeforeQuery = unreachable.isRunning();
                JdbcTemplate unreachableJdbcTemplate = mainDbConfig.mainJdbcTemplate(unreachable);

                // then
                assertThat(runningBeforeQuery).isFalse();
                assertThatThrownBy(() -> unreachableJdbcTemplate.queryForObject("SELECT 1", Integer.class))
                        .isInstanceOf(DataAccessException.class);
            }
        }

        @Test
        void health는_메인_DB와_상관없이_UP이다() {
            // when
            Status status = healthEndpoint.health().getStatus();

            // then
            assertThat(status).isEqualTo(Status.UP);
        }
    }
}
