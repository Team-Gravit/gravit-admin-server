package gravit.admin.support;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 테스트에서 메인 DB 픽스처 스키마에 행을 넣는 쓰기 연결. 애플리케이션의 @MainDb 연결은 읽기 전용이라 따로 둔다.
 */
public class MainDbFixtureWriter {

    private final JdbcTemplate jdbcTemplate;

    public MainDbFixtureWriter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public int update(
            String sql,
            Object... args
    ) {
        return jdbcTemplate.update(sql, args);
    }

    public void execute(String sql) {
        jdbcTemplate.execute(sql);
    }
}
