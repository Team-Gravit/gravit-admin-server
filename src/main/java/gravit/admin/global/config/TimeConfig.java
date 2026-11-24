package gravit.admin.global.config;

import gravit.admin.global.util.TimeZoneConst;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.system(TimeZoneConst.KST);
    }
}
