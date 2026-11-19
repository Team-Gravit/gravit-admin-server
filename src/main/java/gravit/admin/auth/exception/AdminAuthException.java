package gravit.admin.auth.exception;

import gravit.admin.global.exception.domain.ErrorCode;
import lombok.Getter;

@Getter
public class AdminAuthException extends RuntimeException {

    private final ErrorCode errorCode;

    public AdminAuthException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
