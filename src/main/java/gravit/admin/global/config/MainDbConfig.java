package gravit.admin.global.config;

import com.zaxxer.hikari.HikariDataSource;
import gravit.admin.global.annotation.MainDb;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.jdbc.DataSourceHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class MainDbConfig {

    private static final String POOL_NAME = "main-db";
    private static final String READ_ONLY_MODE_PROPERTY = "readOnlyMode";
    private static final String READ_ONLY_MODE_ALWAYS = "always";

    @MainDb
    @Bean(defaultCandidate = false)
    public HikariDataSource mainDataSource(MainDbProps mainDbProps) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName(POOL_NAME);
        dataSource.setJdbcUrl(mainDbProps.url());
        dataSource.setUsername(mainDbProps.username());
        dataSource.setPassword(mainDbProps.password());
        dataSource.setMaximumPoolSize(mainDbProps.maximumPoolSize());
        dataSource.setMinimumIdle(mainDbProps.minimumIdle());
        dataSource.setReadOnly(true);
        dataSource.addDataSourceProperty(READ_ONLY_MODE_PROPERTY, READ_ONLY_MODE_ALWAYS);
        return dataSource;
    }

    @MainDb
    @Bean(defaultCandidate = false)
    public JdbcTemplate mainJdbcTemplate(@MainDb DataSource mainDataSource) {
        return new JdbcTemplate(mainDataSource);
    }

    @Bean
    public HealthContributor dbHealthContributor(DataSource dataSource) {
        return new DataSourceHealthIndicator(dataSource);
    }
}
