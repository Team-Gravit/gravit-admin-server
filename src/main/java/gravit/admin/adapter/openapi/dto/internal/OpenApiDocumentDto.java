package gravit.admin.adapter.openapi.dto.internal;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenApiDocumentDto(
        String url,
        JsonNode document,
        String rawJson
) {
}
