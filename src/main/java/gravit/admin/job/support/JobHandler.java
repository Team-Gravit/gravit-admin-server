package gravit.admin.job.support;

import gravit.admin.job.domain.JobType;

public interface JobHandler {

    JobType type();

    void handle(JobContext context);
}
