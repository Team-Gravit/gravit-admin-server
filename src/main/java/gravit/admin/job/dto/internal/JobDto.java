package gravit.admin.job.dto.internal;

import gravit.admin.job.domain.JobStatus;
import gravit.admin.job.domain.JobType;

import java.time.LocalDateTime;

public record JobDto(
        long id,
        JobType type,
        Long sprintId,
        JobStatus status,
        int attempt,
        int maxAttempts,
        LocalDateTime runAfter,
        boolean cancelRequested,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
