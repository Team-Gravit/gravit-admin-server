package gravit.admin.adapter.gravit.dto.internal;

import org.springframework.http.HttpMethod;

public record GravitRequestDto(
        HttpMethod method,
        String path,
        String query,
        String contentType,
        String accept,
        byte[] body
) {

    public static GravitRequestDto of(
            HttpMethod method,
            String path,
            String query,
            String contentType,
            String accept,
            byte[] body
    ) {
        return new GravitRequestDto(method, path, query, contentType, accept, body == null ? new byte[0] : body);
    }

    public boolean hasBody() {
        return body.length > 0;
    }
}
