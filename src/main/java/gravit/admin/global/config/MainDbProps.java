package gravit.admin.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("qa.main-db")
public record MainDbProps(
        @NotBlank
        String url,

        @NotBlank
        String username,

        @NotNull
        String password,

        @Positive @DefaultValue("3")
        int maximumPoolSize,

        @PositiveOrZero @DefaultValue("1")
        int minimumIdle
) {

    @Override
    public String toString() {
        return "MainDbProps[url=" + url + ", username=" + username + ", maximumPoolSize=" + maximumPoolSize
                + ", minimumIdle=" + minimumIdle + "]";
    }
}
