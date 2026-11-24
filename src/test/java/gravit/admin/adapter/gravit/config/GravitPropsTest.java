package gravit.admin.adapter.gravit.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

class GravitPropsTest {

    @Test
    void 메인_API_호스트는_판정_호스트_설정값을_그대로_쓴다() throws IOException {
        // given
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        PropertySource<?> application = sources.getFirst();

        // when & then
        assertSoftly(softly -> {
            softly.assertThat(application.getProperty("qa.adapter.gravit.host")).hasToString("${qa.auth.host}");
            softly.assertThat(application.getProperty("qa.adapter.gravit.connect-timeout")).hasToString("2s");
            softly.assertThat(application.getProperty("qa.adapter.gravit.read-timeout")).hasToString("10s");
        });
    }
}
