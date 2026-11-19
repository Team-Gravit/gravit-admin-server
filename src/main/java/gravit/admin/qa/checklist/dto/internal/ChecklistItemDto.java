package gravit.admin.qa.checklist.dto.internal;

import gravit.admin.qa.checklist.domain.ChecklistItem;
import gravit.admin.qa.checklist.domain.Classification;
import gravit.admin.qa.feature.domain.Part;

import java.util.List;

public record ChecklistItemDto(
        long id,
        long featureId,
        String title,
        String expected,
        List<Part> parts,
        Classification classification,
        List<String> specRowIds
) {

    public static ChecklistItemDto from(ChecklistItem item) {
        return new ChecklistItemDto(
                item.getId(),
                item.getFeatureId(),
                item.getTitle(),
                item.getExpected(),
                List.copyOf(item.getParts()),
                item.getClassification(),
                List.copyOf(item.getSpecRowIds())
        );
    }
}
