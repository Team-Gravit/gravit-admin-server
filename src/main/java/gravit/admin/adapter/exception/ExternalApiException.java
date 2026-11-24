package gravit.admin.adapter.exception;

import gravit.admin.adapter.domain.ExternalApi;
import lombok.Getter;

@Getter
public class ExternalApiException extends RuntimeException {

    private final ExternalApi api;
    private final Integer status;

    public ExternalApiException(
            ExternalApi api,
            Integer status,
            String message
    ) {
        super(message);
        this.api = api;
        this.status = status;
    }
}
