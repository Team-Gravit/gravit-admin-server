package gravit.admin.global.exception.handler;

import gravit.admin.global.exception.domain.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static gravit.admin.global.exception.domain.CustomErrorCode.ADMIN_API_INVALID_PARAMS;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {
        "gravit.admin.insight",
        "gravit.admin.support",
        "gravit.admin.member",
        "gravit.admin.content",
        "gravit.admin.audit"
})
public class AdminApiExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse<List<String>>> handleConstraintViolationException(ConstraintViolationException e) {
        List<String> messages = e.getConstraintViolations().stream()
                .map(this::toViolationMessage)
                .toList();

        return ResponseEntity.status(ADMIN_API_INVALID_PARAMS.getHttpStatus())
                .body(ErrorResponse.of(ADMIN_API_INVALID_PARAMS.getCode(), messages));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse<String>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(ADMIN_API_INVALID_PARAMS.getHttpStatus())
                .body(ErrorResponse.of(ADMIN_API_INVALID_PARAMS.getCode(), ADMIN_API_INVALID_PARAMS.getMessage()));
    }

    private String toViolationMessage(ConstraintViolation<?> violation) {
        return "[ " + leafName(violation.getPropertyPath()) + " ][ " + violation.getMessage() + " ][ " + violation.getInvalidValue() + " ]";
    }

    private String leafName(Path propertyPath) {
        String leafName = null;
        for (Path.Node node : propertyPath) {
            leafName = node.getName();
        }
        return leafName;
    }
}
