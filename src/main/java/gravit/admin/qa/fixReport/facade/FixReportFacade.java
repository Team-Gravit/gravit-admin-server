package gravit.admin.qa.fixReport.facade;

import gravit.admin.qa.checklist.domain.CellState;
import gravit.admin.qa.checklist.domain.Classification;
import gravit.admin.qa.checklist.dto.internal.FailedCellDto;
import gravit.admin.qa.checklist.service.ChecklistQueryService;
import gravit.admin.qa.checklist.support.ProgressCalculator;
import gravit.admin.qa.designReview.dto.internal.FailedDesignItemDto;
import gravit.admin.qa.designReview.service.DesignReviewQueryService;
import gravit.admin.qa.feature.domain.Part;
import gravit.admin.qa.feature.dto.internal.FeatureDto;
import gravit.admin.qa.feature.service.FeatureQueryService;
import gravit.admin.qa.fixReport.dto.internal.FixReportDto;
import gravit.admin.qa.fixReport.dto.internal.FixReportFileDto;
import gravit.admin.qa.fixReport.dto.response.FixReportPartResponse;
import gravit.admin.qa.fixReport.dto.response.FixReportResponse;
import gravit.admin.qa.fixReport.dto.response.FixReportUncheckedResponse;
import gravit.admin.qa.fixReport.support.FixReportAssembler;
import gravit.admin.qa.fixReport.support.FixReportFileName;
import gravit.admin.qa.fixReport.support.FixReportWriter;
import gravit.admin.global.annotation.Facade;
import gravit.admin.global.exception.domain.RestApiException;
import gravit.admin.job.service.JobQueryService;
import gravit.admin.qa.sprint.domain.ConnectionTarget;
import gravit.admin.qa.sprint.domain.ProgressPart;
import gravit.admin.qa.sprint.domain.SprintStatus;
import gravit.admin.qa.sprint.dto.internal.ConnectionResultDto;
import gravit.admin.qa.sprint.dto.internal.SprintDto;
import gravit.admin.qa.sprint.service.SprintQueryService;
import gravit.admin.qa.sprint.support.ConnectCollector;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static gravit.admin.global.exception.domain.CustomErrorCode.FIX_REPORT_NOT_READY;

@Facade
@RequiredArgsConstructor
public class FixReportFacade {

    private final SprintQueryService sprintQueryService;
    private final ChecklistQueryService checklistQueryService;
    private final DesignReviewQueryService designReviewQueryService;
    private final FeatureQueryService featureQueryService;
    private final JobQueryService jobQueryService;

    public FixReportResponse getFixReports(long sprintId) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);

        FixReportUncheckedResponse unchecked = countUnchecked(sprintId);
        boolean ready = isReady(sprint, unchecked);

        Map<Part, Map<CellState, Long>> partToCounts = checklistQueryService.countStates(sprintId);
        Map<CellState, Long> designCounts = designReviewQueryService.countStates(sprintId);
        List<FixReportPartResponse> parts = Arrays.stream(ProgressPart.values())
                .map(part -> FixReportPartResponse.of(
                        part,
                        ProgressCalculator.count(part == ProgressPart.DESIGN ? designCounts : partToCounts.getOrDefault(toPart(part), Map.of())).fail(),
                        FixReportFileName.of(sprint.id(), sprint.sprintKey(), part)
                ))
                .toList();

        return FixReportResponse.of(sprint.id(), sprint.status(), ready, unchecked, parts);
    }

    public FixReportFileDto getFixReportFile(
            long sprintId,
            ProgressPart part
    ) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);
        if (!isReady(sprint, countUnchecked(sprintId))) {
            throw new RestApiException(FIX_REPORT_NOT_READY);
        }

        FixReportDto report = part == ProgressPart.DESIGN ? designReport(sprint) : codeReport(sprint, part);

        return FixReportFileDto.of(FixReportFileName.of(sprint.id(), sprint.sprintKey(), part), FixReportWriter.write(report));
    }

    private FixReportDto codeReport(
            SprintDto sprint,
            ProgressPart part
    ) {
        ConnectionTarget target = ConnectionTarget.valueOf(part.name());
        String ref = sprintQueryService.findConnection(sprint.id(), target)
                .map(ConnectionResultDto::ref)
                .orElse(null);

        List<FailedCellDto> cells = checklistQueryService.getFailedCells(sprint.id(), toPart(part));
        Map<Long, FeatureDto> featureIdToFeature = features(cells.stream().map(FailedCellDto::featureId).collect(Collectors.toSet()));

        return FixReportAssembler.assembleCode(
                sprint.sprintKey(),
                part,
                ConnectCollector.TARGET_TO_REPO.get(target).getRepoName(),
                ref,
                cells,
                featureIdToFeature
        );
    }

    private FixReportDto designReport(SprintDto sprint) {
        List<FailedDesignItemDto> items = designReviewQueryService.getFailedItems(sprint.id());
        Map<Long, FeatureDto> featureIdToFeature = features(items.stream().map(FailedDesignItemDto::featureId).collect(Collectors.toSet()));

        return FixReportAssembler.assembleDesign(sprint.sprintKey(), sprint.figmaFileKey(), items, featureIdToFeature);
    }

    private Map<Long, FeatureDto> features(Collection<Long> featureIds) {
        return featureQueryService.getFeatures(featureIds).stream()
                .collect(Collectors.toMap(FeatureDto::id, Function.identity()));
    }

    private FixReportUncheckedResponse countUnchecked(long sprintId) {
        Map<Classification, Long> classificationToCount = checklistQueryService.countUncheckedByClassification(sprintId);
        return FixReportUncheckedResponse.of(
                designReviewQueryService.countUnchecked(sprintId),
                classificationToCount.getOrDefault(Classification.AUTO, 0L),
                classificationToCount.getOrDefault(Classification.MANUAL, 0L)
        );
    }

    private boolean isReady(
            SprintDto sprint,
            FixReportUncheckedResponse unchecked
    ) {
        if (sprint.status() == SprintStatus.CLOSED) {
            return true;
        }
        if (sprint.status() != SprintStatus.MANUAL_REVIEW) {
            return false;
        }
        boolean allChecked = unchecked.design() == 0 && unchecked.first() == 0 && unchecked.second() == 0;
        return allChecked && !jobQueryService.hasActiveJob(sprint.id());
    }

    private Part toPart(ProgressPart part) {
        return Part.valueOf(part.name());
    }
}
