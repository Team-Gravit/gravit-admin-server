package gravit.admin.qa.sprint.support;

import gravit.admin.global.util.KstTimes;
import gravit.admin.job.support.JobCancelledException;
import gravit.admin.job.support.JobContext;
import gravit.admin.qa.sprint.domain.SetupStepKey;
import gravit.admin.qa.sprint.domain.SetupStepStatus;
import gravit.admin.qa.sprint.dto.internal.SetupTargetDto;
import gravit.admin.qa.sprint.service.SetupStepService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class SetupStepRunner {

    public static final String SKIPPED_MESSAGE = "이전 실행에서 끝난 단계";
    public static final String CANCELLED_MESSAGE = "작업이 취소되었습니다";

    private final SetupStepService setupStepService;

    private final Clock clock;

    public void run(
            JobContext context,
            SetupTargetDto target,
            SetupStepKey key,
            Runnable work
    ) {
        context.checkCancelled();
        long sprintId = target.sprintId();
        if (target.status().ordinal() > key.getSprintStatus().ordinal() || setupStepService.isDone(sprintId, key)) {
            context.skipStep(key.name(), SKIPPED_MESSAGE);
            return;
        }

        setupStepService.start(sprintId, key, KstTimes.now(clock));
        try {
            context.step(key.name(), work);
        } catch (RuntimeException e) {
            setupStepService.finish(sprintId, key, SetupStepStatus.FAILED, messageOf(e), KstTimes.now(clock));
            throw e;
        }
    }

    private String messageOf(RuntimeException exception) {
        if (exception instanceof JobCancelledException) {
            return CANCELLED_MESSAGE;
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
