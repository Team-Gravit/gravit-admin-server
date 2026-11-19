package gravit.admin.qa.sprint.handler;

import gravit.admin.job.domain.JobType;
import gravit.admin.job.support.JobContext;
import gravit.admin.job.support.JobHandler;
import gravit.admin.job.support.NonRetryableJobException;
import gravit.admin.qa.sprint.domain.SetupStepKey;
import gravit.admin.qa.sprint.dto.internal.SetupTargetDto;
import gravit.admin.qa.sprint.service.SprintQueryService;
import gravit.admin.qa.sprint.support.AutoReviewStep;
import gravit.admin.qa.sprint.support.ChecklistGenerateStep;
import gravit.admin.qa.sprint.support.DesignReviewStep;
import gravit.admin.qa.sprint.support.SetupStepRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QaPrepareJobHandler implements JobHandler {

    private final SprintQueryService sprintQueryService;

    private final SetupStepRunner setupStepRunner;
    private final ChecklistGenerateStep checklistGenerateStep;
    private final DesignReviewStep designReviewStep;
    private final AutoReviewStep autoReviewStep;

    @Override
    public JobType type() {
        return JobType.QA_PREPARE;
    }

    @Override
    public void handle(JobContext context) {
        if (context.sprintId() == null) {
            throw new NonRetryableJobException("QA 준비 작업에 스프린트가 없습니다. jobId=" + context.jobId());
        }
        SetupTargetDto target = sprintQueryService.getSetupTarget(context.sprintId());
        if (!target.status().isQaPreparing()) {
            throw new NonRetryableJobException("QA 준비 중인 스프린트가 아닙니다. sprintId=" + target.sprintId() + ", status=" + target.status());
        }

        setupStepRunner.run(context, target, SetupStepKey.CHECKLIST_GENERATE, () -> checklistGenerateStep.run(context, target));
        setupStepRunner.run(context, target, SetupStepKey.DESIGN_REVIEW, () -> designReviewStep.run(context, target));
        setupStepRunner.run(context, target, SetupStepKey.AUTO_REVIEW, () -> autoReviewStep.run(context, target));
    }
}
