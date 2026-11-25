package gravit.admin.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class MainDbPropsTest {

    private static final String PASSWORD = "main-db-secret";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropsConfig.class);

    @Test
    void 주소와_계정이_없으면_기동하지_않는다() {
        // when & then
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 풀_크기를_주지_않으면_최대_3개_최소_1개다() {
        // when & then
        contextRunner.withPropertyValues(
                        "qa.main-db.url=jdbc:postgresql://localhost:5432/gravit",
                        "qa.main-db.username=gravit_admin_ro",
                        "qa.main-db.password=" + PASSWORD)
                .run(context -> {
                    MainDbProps props = context.getBean(MainDbProps.class);
                    assertSoftly(softly -> {
                        softly.assertThat(props.maximumPoolSize()).isEqualTo(3);
                        softly.assertThat(props.minimumIdle()).isEqualTo(1);
                        softly.assertThat(props.toString()).doesNotContain(PASSWORD);
                    });
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MainDbProps.class)
    static class PropsConfig {
    }
}
