package gravit.admin.insight;

import gravit.admin.global.exception.domain.RestApiException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static gravit.admin.global.exception.domain.CustomErrorCode.RESOURCE_NOT_FOUND;

/**
 * 이관 모듈(insight) 패키지에 있는 테스트용 Controller. AdminApiExceptionHandler 적용 범위 확인용으로 @Import 해서만 쓴다.
 */
@TestComponent
@Validated
@RestController
public class AdminApiErrorProbeController {

    public static final String BASE_PATH = "/api/v1/admin/error-probe";

    @GetMapping(BASE_PATH + "/days")
    public int getDays(@RequestParam("days") @Min(1) @Max(365) int days) {
        return days;
    }

    @GetMapping(BASE_PATH + "/items/{itemId}")
    public long getItem(@PathVariable("itemId") long itemId) {
        return itemId;
    }

    @GetMapping(BASE_PATH + "/missing")
    public void getMissing() {
        throw new RestApiException(RESOURCE_NOT_FOUND);
    }

    @PostMapping(BASE_PATH + "/items")
    public Map<String, Object> createItem(@RequestBody Map<String, Object> body) {
        return body;
    }
}
