package gravit.admin.support;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * 테스트가 쓰는 PostgreSQL 하나. ADMIN_TEST_DB_URL이 있으면 그 DB, 없으면 Testcontainers를 JVM마다 한 번 띄운다.
 * 관리자 DB는 public 스키마, 메인 DB 픽스처는 같은 DB의 maindb 스키마(MainDbFixtureSchema)를 쓴다.
 */
public final class TestDatabase {

    public static final String MAIN_DB_SCHEMA = "maindb";
    public static final String MAIN_DB_FIXTURE_LOCATION = "classpath*:maindb/*.sql";

    private static final String EXTERNAL_URL_ENV = "ADMIN_TEST_DB_URL";
    private static final String EXTERNAL_USERNAME_ENV = "ADMIN_TEST_DB_USERNAME";
    private static final String EXTERNAL_PASSWORD_ENV = "ADMIN_TEST_DB_PASSWORD";
    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String CURRENT_SCHEMA_PARAM = "currentSchema=";

    private final String url;
    private final String username;
    private final String password;

    private TestDatabase(
            String url,
            String username,
            String password
    ) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static TestDatabase get() {
        return Holder.INSTANCE;
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String mainDbUrl() {
        return withCurrentSchema(MAIN_DB_SCHEMA);
    }

    public DataSource writableDataSource(String schema) {
        return new DriverManagerDataSource(withCurrentSchema(schema), username, password);
    }

    private String withCurrentSchema(String schema) {
        String delimiter = url.contains("?") ? "&" : "?";
        return url + delimiter + CURRENT_SCHEMA_PARAM + schema;
    }

    private static TestDatabase create() {
        String externalUrl = System.getenv(EXTERNAL_URL_ENV);
        TestDatabase database;
        if (externalUrl != null && !externalUrl.isBlank()) {
            database = new TestDatabase(externalUrl, envOrDefault(EXTERNAL_USERNAME_ENV, "postgres"), envOrDefault(EXTERNAL_PASSWORD_ENV, ""));
        } else {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGRES_IMAGE);
            container.start();
            database = new TestDatabase(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }

        MainDbFixtureSchema.prepare(database.writableDataSource(MAIN_DB_SCHEMA), MAIN_DB_SCHEMA, MAIN_DB_FIXTURE_LOCATION);
        return database;
    }

    private static String envOrDefault(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static final class Holder {

        private static final TestDatabase INSTANCE = create();
    }
}
