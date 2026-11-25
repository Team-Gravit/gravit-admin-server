package gravit.admin.global.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PageResponse<T>(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int page,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> contents
) {

    public static <T> PageResponse<T> of(
            int page,
            int pageSize,
            long totalElements,
            List<T> contents
    ) {
        int totalPages = (int) Math.ceil((double) totalElements / (double) pageSize);
        return new PageResponse<>(page, totalPages, page < totalPages, totalElements, contents);
    }
}
