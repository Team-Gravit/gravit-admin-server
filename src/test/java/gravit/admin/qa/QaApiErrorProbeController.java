package gravit.admin.qa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * QA 모듈 패키지에 있는 테스트용 Controller. AdminApiExceptionHandler 적용 범위 밖임을 확인하는 데만 @Import 해서 쓴다.
 */
@TestComponent
@Validated
@RestController
public class QaApiErrorProbeController {

    public static final String BASE_PATH = "/api/v1/qa-error-probe";

    @GetMapping(BASE_PATH + "/days")
    public int getDays(@RequestParam("days") @Min(1) @Max(365) int days) {
        return days;
    }

    @GetMapping(BASE_PATH + "/items/{itemId}")
    public long getItem(@PathVariable("itemId") long itemId) {
        return itemId;
    }
}
