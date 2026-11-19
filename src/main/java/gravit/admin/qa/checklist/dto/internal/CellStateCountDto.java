package gravit.admin.qa.checklist.dto.internal;

import gravit.admin.qa.checklist.domain.CellState;
import gravit.admin.qa.feature.domain.Part;

public record CellStateCountDto(
        Part part,
        CellState state,
        long count
) {
}
