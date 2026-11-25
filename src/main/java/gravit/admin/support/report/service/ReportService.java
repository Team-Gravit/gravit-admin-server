package gravit.admin.support.report.service;

import gravit.admin.global.dto.response.PageResponse;
import gravit.admin.global.exception.domain.RestApiException;
import gravit.admin.global.util.AdminPages;
import gravit.admin.support.report.domain.ReportType;
import gravit.admin.support.report.dto.response.ReportDetailResponse;
import gravit.admin.support.report.dto.response.ReportListItemResponse;
import gravit.admin.support.report.repository.custom.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static gravit.admin.global.exception.domain.CustomErrorCode.REPORT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    public PageResponse<ReportListItemResponse> getReports(
            int page,
            ReportType reportType,
            Boolean isResolved
    ) {
        long offset = AdminPages.offsetOf(page);

        List<ReportListItemResponse> contents = reportRepository.findPage(reportType, isResolved, offset, AdminPages.PAGE_SIZE);
        long totalElements = reportRepository.count(reportType, isResolved);

        return PageResponse.of(page, AdminPages.PAGE_SIZE, totalElements, contents);
    }

    public ReportDetailResponse getReport(long reportId) {
        return reportRepository.findDetailById(reportId)
                .orElseThrow(() -> new RestApiException(REPORT_NOT_FOUND));
    }
}
