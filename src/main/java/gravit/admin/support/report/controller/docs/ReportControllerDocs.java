package gravit.admin.support.report.controller.docs;

import gravit.admin.global.dto.response.PageResponse;
import gravit.admin.support.report.domain.ReportType;
import gravit.admin.support.report.dto.response.ReportDetailResponse;
import gravit.admin.support.report.dto.response.ReportListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Report API", description = "백오피스 신고 관리")
public interface ReportControllerDocs {

    @Operation(summary = "신고 목록", description = "reportType, isResolved 필터, 최신순(id DESC), 한 페이지 20건.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "page 가 1보다 작음(PAGE_4001), 파라미터 형식 오류(GLOBAL_4001)")
    })
    ResponseEntity<PageResponse<ReportListItemResponse>> getReports(
            @Parameter(description = "페이지 번호 (1부터)", example = "1") int page,
            @Parameter(description = "신고 유형 필터") ReportType reportType,
            @Parameter(description = "처리 여부 필터") Boolean isResolved
    );

    @Operation(summary = "신고 상세", description = "신고자(userId)는 미노출.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 신고(REPORT_4041)")
    })
    ResponseEntity<ReportDetailResponse> getReport(@Parameter(description = "신고 ID") Long reportId);
}
