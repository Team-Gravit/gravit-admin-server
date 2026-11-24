package gravit.admin.qa.autoReview.dto.internal;

import java.util.List;

public record CodeChunksDto(
        List<CodeChunkDto> chunks,
        List<String> droppedFiles
) {
}
