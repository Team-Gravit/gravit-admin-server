package gravit.admin.qa.sprint.dto.internal;

import gravit.admin.qa.feature.dto.internal.SpecPageDto;

import java.util.List;

public record SpecCollectDto(
        List<SpecPageDto> pages,
        List<SetupSnapshotDto> snapshots
) {
}
