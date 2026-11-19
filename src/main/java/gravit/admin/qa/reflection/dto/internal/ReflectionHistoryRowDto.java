package gravit.admin.qa.reflection.dto.internal;

import gravit.admin.adapter.notion.dto.internal.NotionHistoryRowDto;

import java.util.List;

public record ReflectionHistoryRowDto(
        String blockId,
        String sprint,
        String change,
        List<String> cells
) {

    public static ReflectionHistoryRowDto from(NotionHistoryRowDto row) {
        return new ReflectionHistoryRowDto(row.blockId(), row.sprint(), row.change(), List.copyOf(row.cells()));
    }
}
