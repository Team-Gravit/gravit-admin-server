package gravit.admin.adapter.notion.dto.internal;

import java.util.List;

public record NotionSpecRowDto(
        String blockId,
        String sectionTitle,
        String rowId,
        List<String> columns,
        List<String> cells
) {
}
