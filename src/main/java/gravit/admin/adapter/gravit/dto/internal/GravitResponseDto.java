package gravit.admin.adapter.gravit.dto.internal;

public record GravitResponseDto(
        int status,
        String contentType,
        byte[] body
) {

    public static GravitResponseDto of(
            int status,
            String contentType,
            byte[] body
    ) {
        return new GravitResponseDto(status, contentType, body);
    }

    public boolean hasBody() {
        return body.length > 0;
    }
}
