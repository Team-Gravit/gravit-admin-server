package gravit.admin.adapter.figma.dto.internal;

import gravit.admin.adapter.figma.domain.FigmaFrameWarningType;
import gravit.admin.adapter.figma.domain.FigmaPlatform;

public record FigmaFrameWarningDto(
        FigmaFrameWarningType type,
        FigmaPlatform platform,
        String nodeId,
        String name
) {
}
