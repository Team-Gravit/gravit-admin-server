package gravit.admin.support.report.controller;

import gravit.admin.global.dto.response.PageResponse;
import gravit.admin.support.report.controller.docs.ReportControllerDocs;
import gravit.admin.support.report.domain.ReportType;
import gravit.admin.support.report.dto.response.ReportDetailResponse;
import gravit.admin.support.report.dto.response.ReportListItemResponse;
import gravit.admin.support.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reports")
public class ReportController implements ReportControllerDocs {

    private static final String LOCAL_ROUTE = "X-Admin-Route=local";

    private final ReportService reportService;

    @GetMapping(headers = LOCAL_ROUTE)
    public ResponseEntity<PageResponse<ReportListItemResponse>> getReports(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "reportType", required = false) ReportType reportType,
            @RequestParam(value = "isResolved", required = false) Boolean isResolved
    ) {
        return ResponseEntity.status(OK).body(reportService.getReports(page, reportType, isResolved));
    }

    @GetMapping(value = "/{reportId}", headers = LOCAL_ROUTE)
    public ResponseEntity<ReportDetailResponse> getReport(@PathVariable("reportId") Long reportId) {
        return ResponseEntity.status(OK).body(reportService.getReport(reportId));
    }
}
