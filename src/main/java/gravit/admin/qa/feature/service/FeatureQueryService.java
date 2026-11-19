package gravit.admin.qa.feature.service;

import gravit.admin.qa.feature.domain.Feature;
import gravit.admin.qa.feature.domain.SprintFeature;
import gravit.admin.qa.feature.dto.internal.FeatureDto;
import gravit.admin.qa.feature.dto.internal.SprintFeatureDto;
import gravit.admin.qa.feature.repository.FeatureRepository;
import gravit.admin.qa.feature.repository.SprintFeatureRepository;
import gravit.admin.global.exception.domain.RestApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static gravit.admin.global.exception.domain.CustomErrorCode.CHECKLIST_FEATURE_NOT_IN_SPRINT;

@Service
@RequiredArgsConstructor
public class FeatureQueryService {

    private final FeatureRepository featureRepository;
    private final SprintFeatureRepository sprintFeatureRepository;

    @Transactional(readOnly = true)
    public List<SprintFeatureDto> getSprintFeatures(long sprintId) {
        List<SprintFeature> sprintFeatures = sprintFeatureRepository.findAllBySprintId(sprintId);
        if (sprintFeatures.isEmpty()) {
            return List.of();
        }
        Map<Long, Feature> featureIdToFeature = featureRepository.findAllByIdIn(sprintFeatures.stream().map(SprintFeature::getFeatureId).toList())
                .stream()
                .collect(Collectors.toMap(Feature::getId, Function.identity()));

        return sprintFeatures.stream()
                .map(sprintFeature -> SprintFeatureDto.of(FeatureDto.from(featureIdToFeature.get(sprintFeature.getFeatureId())), sprintFeature))
                .sorted(Comparator.comparing(SprintFeatureDto::screenId))
                .toList();
    }

    @Transactional(readOnly = true)
    public SprintFeatureDto getSprintFeature(
            long sprintId,
            long featureId
    ) {
        SprintFeature sprintFeature = sprintFeatureRepository.findBySprintIdAndFeatureId(sprintId, featureId)
                .orElseThrow(() -> new RestApiException(CHECKLIST_FEATURE_NOT_IN_SPRINT));
        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new RestApiException(CHECKLIST_FEATURE_NOT_IN_SPRINT));
        return SprintFeatureDto.of(FeatureDto.from(feature), sprintFeature);
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> getFeatures(Collection<Long> featureIds) {
        if (featureIds.isEmpty()) {
            return List.of();
        }
        return featureRepository.findAllByIdIn(featureIds).stream()
                .map(FeatureDto::from)
                .sorted(Comparator.comparing(FeatureDto::screenId))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isSprintFeature(
            long sprintId,
            long featureId
    ) {
        return sprintFeatureRepository.existsBySprintIdAndFeatureId(sprintId, featureId);
    }
}
