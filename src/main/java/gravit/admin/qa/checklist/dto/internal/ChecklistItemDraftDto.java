package gravit.admin.qa.checklist.dto.internal;

import gravit.admin.qa.checklist.domain.Classification;
import gravit.admin.qa.feature.domain.Part;

import java.util.List;

public record ChecklistItemDraftDto(
        String title,
        String expected,
        List<Part> parts,
        Classification classification,
        List<String> specRowIds
) {

    public static ChecklistItemDraftDto of(
            String title,
            String expected,
            List<Part> parts,
            Classification classification,
            List<String> specRowIds
    ) {
        return new ChecklistItemDraftDto(title, expected, List.copyOf(parts), classification, List.copyOf(specRowIds));
    }
}
