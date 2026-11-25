package gravit.admin.support;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 메인 테이블 DDL 픽스처를 별도 스키마에 깐다. 스키마를 지우고 다시 만든 뒤 파일 이름 순서로 실행한다.
 * dataSource는 그 스키마를 currentSchema로 둔 쓰기 가능한 연결이어야 한다(TestDatabase.writableDataSource).
 */
public final class MainDbFixtureSchema {

    private MainDbFixtureSchema() {
    }

    public static List<String> prepare(
            DataSource dataSource,
            String schema,
            String location
    ) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + schema);

        List<Resource> scripts = findScripts(location);
        try (Connection connection = dataSource.getConnection()) {
            for (Resource script : scripts) {
                ScriptUtils.executeSqlScript(connection, new EncodedResource(script, StandardCharsets.UTF_8));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("메인 DB 픽스처를 깔지 못했습니다: " + schema, e);
        }
        return scripts.stream().map(Resource::getFilename).toList();
    }

    private static List<Resource> findScripts(String location) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(location);
            return Arrays.stream(resources)
                    .sorted(Comparator.comparing(resource -> Objects.requireNonNull(resource.getFilename())))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
