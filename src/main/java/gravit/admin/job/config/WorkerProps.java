package gravit.admin.job.config;

import gravit.admin.job.domain.JobType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("qa.worker")
public record WorkerProps(
        @DefaultValue("true")
        boolean enabled,

        String id,

        @DefaultValue
        List<JobType> jobTypes
) {
}
