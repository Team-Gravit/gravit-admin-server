package gravit.admin.job.domain;

public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }
}
