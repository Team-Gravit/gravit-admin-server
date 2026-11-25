package gravit.admin.support.report.repository.custom;

import gravit.admin.support.report.domain.ReportType;
import gravit.admin.support.report.dto.response.ReportDetailResponse;
import gravit.admin.support.report.dto.response.ReportListItemResponse;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {

    List<ReportListItemResponse> findPage(
            ReportType reportType,
            Boolean isResolved,
            long offset,
            int limit
    );

    long count(
            ReportType reportType,
            Boolean isResolved
    );

    Optional<ReportDetailResponse> findDetailById(long reportId);
}
