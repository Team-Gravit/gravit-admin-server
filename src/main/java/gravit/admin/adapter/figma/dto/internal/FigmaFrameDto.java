package gravit.admin.adapter.figma.dto.internal;

public record FigmaFrameDto(
        String nodeId,
        String name,
        String state,
        String sectionName,
        Double width
) {
}
