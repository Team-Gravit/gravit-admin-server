package gravit.admin.qa.reflection.facade;

import gravit.admin.adapter.exception.ExternalApiException;
import gravit.admin.adapter.exception.InvalidNotionSpecException;
import gravit.admin.adapter.notion.infrastructure.NotionClient;
import gravit.admin.qa.feature.dto.internal.FeatureDto;
import gravit.admin.qa.feature.dto.internal.SpecPageDto;
import gravit.admin.qa.feature.dto.internal.SprintFeatureDto;
import gravit.admin.qa.feature.service.FeatureCommandService;
import gravit.admin.qa.feature.service.FeatureQueryService;
import gravit.admin.qa.feature.support.SpecPageParser;
import gravit.admin.global.annotation.Facade;
import gravit.admin.global.exception.domain.RestApiException;
import gravit.admin.global.util.KstTimes;
import gravit.admin.job.domain.JobType;
import gravit.admin.job.service.JobQueryService;
import gravit.admin.job.support.NonRetryableJobException;
import gravit.admin.qa.reflection.dto.internal.SpecReflectionDto;
import gravit.admin.qa.reflection.dto.response.ReflectionResponse;
import gravit.admin.qa.reflection.service.ReflectionCommandService;
import gravit.admin.qa.reflection.service.ReflectionQueryService;
import gravit.admin.qa.reflection.support.ReflectionApplier;
import gravit.admin.qa.reflection.support.ReflectionStaleException;
import gravit.admin.qa.sprint.domain.SprintStatus;
import gravit.admin.qa.sprint.dto.internal.SprintDto;
import gravit.admin.qa.sprint.facade.SetupFacade;
import gravit.admin.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static gravit.admin.global.exception.domain.CustomErrorCode.REFLECTION_NOTION_WRITE_FAILED;
import static gravit.admin.global.exception.domain.CustomErrorCode.REFLECTION_SPEC_CHANGED;
import static gravit.admin.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;

@Slf4j
@Facade
@RequiredArgsConstructor
public class ReflectionFacade {

    private static final String NOTION_FAILED_MESSAGE = "Notion 반영 실패: ";

    private final SetupFacade setupFacade;

    private final ReflectionQueryService reflectionQueryService;
    private final ReflectionCommandService reflectionCommandService;
    private final SprintQueryService sprintQueryService;
    private final FeatureQueryService featureQueryService;
    private final FeatureCommandService featureCommandService;
    private final JobQueryService jobQueryService;

    private final ReflectionApplier reflectionApplier;
    private final NotionClient notionClient;

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public List<ReflectionResponse> getReflections(long sprintId) {
        sprintQueryService.getSprint(sprintId);
        List<SpecReflectionDto> reflections = reflectionQueryService.getReflections(sprintId);
        Map<Long, FeatureDto> featureIdToFeature = featureQueryService.getFeatures(reflections.stream().map(SpecReflectionDto::featureId).toList()).stream()
                .collect(Collectors.toMap(FeatureDto::id, Function.identity()));

        return reflections.stream()
                .map(reflection -> ReflectionResponse.of(reflection, featureIdToFeature.get(reflection.featureId())))
                .toList();
    }

    public ReflectionResponse approve(
            long reflectionId,
            int version,
            long adminId
    ) {
        SpecReflectionDto reflection = reflectionQueryService.getReflection(reflectionId);
        SprintDto sprint = requireDecidable(reflection.sprintId());
        SprintFeatureDto feature = featureQueryService.getSprintFeature(reflection.sprintId(), reflection.featureId());
        String pageId = feature.feature().notionPageId();

        SpecReflectionDto started = reflectionCommandService.startApply(reflectionId, version);
        try {
            reflectionApplier.apply(started, pageId, (index, createdBlockId) -> reflectionCommandService.markOperationApplied(reflectionId, index, createdBlockId));
            SpecPageDto page = SpecPageParser.parse(notionClient.getSpec(pageId), notionClient.getPageBody(pageId));

            transactionTemplate.executeWithoutResult(status -> {
                featureCommandService.updateSpec(sprint.id(), page.toUpsertDto(), page.toSprintFeatureSpec(feature.featureId(), sprint.sprintKey()));
                reflectionCommandService.complete(reflectionId, adminId, KstTimes.now(clock));
            });
        } catch (ReflectionStaleException e) {
            reflectionCommandService.fail(reflectionId, e.getMessage(), adminId, KstTimes.now(clock));
            throw new RestApiException(REFLECTION_SPEC_CHANGED);
        } catch (ExternalApiException | NonRetryableJobException | InvalidNotionSpecException e) {
            log.warn("수정 이력 반영 실패. reflectionId={}, reason={}", reflectionId, e.getMessage());
            reflectionCommandService.fail(reflectionId, NOTION_FAILED_MESSAGE + e.getMessage(), adminId, KstTimes.now(clock));
            throw new RestApiException(REFLECTION_NOTION_WRITE_FAILED);
        }

        setupFacade.proceedAfterReflections(sprint.id());
        return toResponse(reflectionId);
    }

    public ReflectionResponse reject(
            long reflectionId,
            int version,
            long adminId
    ) {
        SpecReflectionDto reflection = reflectionQueryService.getReflection(reflectionId);
        SprintDto sprint = requireDecidable(reflection.sprintId());
        SpecPageDto page = reflection.hasAppliedOperation() ? readPage(reflection) : null;

        transactionTemplate.executeWithoutResult(status -> {
            if (page != null) {
                featureCommandService.updateSpec(sprint.id(), page.toUpsertDto(), page.toSprintFeatureSpec(reflection.featureId(), sprint.sprintKey()));
            }
            reflectionCommandService.reject(reflectionId, version, adminId, KstTimes.now(clock));
        });

        setupFacade.proceedAfterReflections(sprint.id());
        return toResponse(reflectionId);
    }

    private SprintDto requireDecidable(long sprintId) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);
        if (sprint.status() != SprintStatus.SPEC_LOAD || jobQueryService.hasActiveJob(sprintId, JobType.SPEC_LOAD)) {
            throw new RestApiException(SPRINT_STATUS_CONFLICT);
        }
        return sprint;
    }

    private SpecPageDto readPage(SpecReflectionDto reflection) {
        String pageId = featureQueryService.getSprintFeature(reflection.sprintId(), reflection.featureId()).feature().notionPageId();
        try {
            return SpecPageParser.parse(notionClient.getSpec(pageId), notionClient.getPageBody(pageId));
        } catch (ExternalApiException | NonRetryableJobException | InvalidNotionSpecException e) {
            log.warn("거절 전 Notion 명세를 다시 읽지 못했습니다. reflectionId={}, reason={}", reflection.id(), e.getMessage());
            throw new RestApiException(REFLECTION_NOTION_WRITE_FAILED);
        }
    }

    private ReflectionResponse toResponse(long reflectionId) {
        SpecReflectionDto reflection = reflectionQueryService.getReflection(reflectionId);
        FeatureDto feature = featureQueryService.getFeatures(List.of(reflection.featureId())).getFirst();
        return ReflectionResponse.of(reflection, feature);
    }
}
