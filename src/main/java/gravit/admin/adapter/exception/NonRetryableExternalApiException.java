package gravit.admin.adapter.exception;

import gravit.admin.adapter.domain.ExternalApi;
import gravit.admin.job.support.NonRetryableJobException;
import lombok.Getter;

@Getter
public class NonRetryableExternalApiException extends NonRetryableJobException {

    private final ExternalApi api;
    private final Integer status;

    public NonRetryableExternalApiException(
            ExternalApi api,
            Integer status,
            String message
    ) {
        super(message);
        this.api = api;
        this.status = status;
    }
}
