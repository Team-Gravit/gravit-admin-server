package gravit.admin.adapter.gravit.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.adapter.gravit")
public record GravitProps(
        @NotBlank
        String host,

        @NotNull @DefaultValue("2s")
        Duration connectTimeout,

        @NotNull @DefaultValue("10s")
        Duration readTimeout
) {
}
