package gravit.admin.qa.checklist.dto.internal;

import gravit.admin.qa.checklist.domain.Classification;

public record ClassificationCountDto(
        Classification classification,
        long count
) {
}
