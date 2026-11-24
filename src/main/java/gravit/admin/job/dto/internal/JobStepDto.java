package gravit.admin.job.dto.internal;

import gravit.admin.job.domain.JobStepStatus;

import java.time.LocalDateTime;

public record JobStepDto(
        int seq,
        String name,
        JobStepStatus status,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
