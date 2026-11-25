package gravit.admin.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.function.Supplier;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    private static final String EXTERNAL_URL_ENV = "ADMIN_TEST_DB_URL";
    private static final String EXTERNAL_USERNAME_ENV = "ADMIN_TEST_DB_USERNAME";
    private static final String EXTERNAL_PASSWORD_ENV = "ADMIN_TEST_DB_PASSWORD";
    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final List<String> DATASOURCE_PREFIXES = List.of("spring.datasource", "qa.main-db");

    @Bean
    public DynamicPropertyRegistrar postgresProperties() {
        return registry -> {
            String externalUrl = System.getenv(EXTERNAL_URL_ENV);
            if (externalUrl != null && !externalUrl.isBlank()) {
                addDataSource(registry, () -> externalUrl, () -> envOrDefault(EXTERNAL_USERNAME_ENV, "postgres"),
                        () -> envOrDefault(EXTERNAL_PASSWORD_ENV, ""));
                return;
            }

            PostgreSQLContainer<?> container = ContainerHolder.POSTGRES;
            addDataSource(registry, container::getJdbcUrl, container::getUsername, container::getPassword);
        };
    }

    private static void addDataSource(
            DynamicPropertyRegistry registry,
            Supplier<Object> url,
            Supplier<Object> username,
            Supplier<Object> password
    ) {
        for (String prefix : DATASOURCE_PREFIXES) {
            registry.add(prefix + ".url", url);
            registry.add(prefix + ".username", username);
            registry.add(prefix + ".password", password);
        }
    }

    private static String envOrDefault(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static final class ContainerHolder {

        private static final PostgreSQLContainer<?> POSTGRES = startPostgres();

        private static PostgreSQLContainer<?> startPostgres() {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGRES_IMAGE);
            container.start();
            return container;
        }
    }
}
