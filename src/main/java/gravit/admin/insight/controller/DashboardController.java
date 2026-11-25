package gravit.admin.insight.controller;

import gravit.admin.insight.controller.docs.DashboardControllerDocs;
import gravit.admin.insight.dto.response.DailyActiveUserTrendResponse;
import gravit.admin.insight.dto.response.DashboardSummaryResponse;
import gravit.admin.insight.dto.response.MonthlyActiveUserTrendResponse;
import gravit.admin.insight.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.OK;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController implements DashboardControllerDocs {

    private static final String LOCAL_ROUTE = "X-Admin-Route=local";

    private final DashboardService dashboardService;

    @GetMapping(value = "/summary", headers = LOCAL_ROUTE)
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.status(OK).body(dashboardService.getSummary());
    }

    @GetMapping(value = "/active-users/daily", headers = LOCAL_ROUTE)
    public ResponseEntity<DailyActiveUserTrendResponse> getDailyActiveUsers(
            @RequestParam(value = "days", defaultValue = "30") int days
    ) {
        return ResponseEntity.status(OK).body(dashboardService.getDailyActiveUsers(days));
    }

    @GetMapping(value = "/active-users/monthly", headers = LOCAL_ROUTE)
    public ResponseEntity<MonthlyActiveUserTrendResponse> getMonthlyActiveUsers(
            @RequestParam(value = "months", defaultValue = "12") int months
    ) {
        return ResponseEntity.status(OK).body(dashboardService.getMonthlyActiveUsers(months));
    }
}
