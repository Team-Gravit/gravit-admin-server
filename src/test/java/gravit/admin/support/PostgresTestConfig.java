package gravit.admin.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    public DynamicPropertyRegistrar postgresProperties() {
        return registry -> {
            TestDatabase database = TestDatabase.get();
            registry.add("spring.datasource.url", database::url);
            registry.add("spring.datasource.username", database::username);
            registry.add("spring.datasource.password", database::password);
            registry.add("qa.main-db.url", database::mainDbUrl);
            registry.add("qa.main-db.username", database::username);
            registry.add("qa.main-db.password", database::password);
        };
    }

    @Bean
    public MainDbFixtureWriter mainDbFixtureWriter() {
        return new MainDbFixtureWriter(TestDatabase.get().writableDataSource(TestDatabase.MAIN_DB_SCHEMA));
    }
}
