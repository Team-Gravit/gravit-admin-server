package gravit.admin.job.dto.internal;

import gravit.admin.job.domain.JobStatus;
import gravit.admin.job.domain.JobType;

public record ReapedJobDto(
        long id,
        JobType type,
        Long sprintId,
        JobStatus status
) {

    public boolean isTerminated() {
        return status == JobStatus.FAILED || status == JobStatus.CANCELLED;
    }
}
