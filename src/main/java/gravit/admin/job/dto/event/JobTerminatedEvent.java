package gravit.admin.job.dto.event;

import gravit.admin.job.domain.JobStatus;
import gravit.admin.job.domain.JobType;

public record JobTerminatedEvent(
        long jobId,
        JobType type,
        Long sprintId,
        JobStatus status,
        Throwable cause
) {

    public static JobTerminatedEvent of(
            long jobId,
            JobType type,
            Long sprintId,
            JobStatus status,
            Throwable cause
    ) {
        return new JobTerminatedEvent(jobId, type, sprintId, status, cause);
    }
}
