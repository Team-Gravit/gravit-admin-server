package gravit.admin.support.report.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import gravit.admin.support.report.domain.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder(access = AccessLevel.PRIVATE)
public record ReportDetailResponse(

        @Schema(description = "신고 ID", example = "12")
        long reportId,

        @Schema(description = "신고 유형", example = "TYPO_ERROR")
        ReportType reportType,

        @Schema(description = "신고된 문제 ID", example = "301")
        long problemId,

        @Schema(description = "신고 내용. 신고자(userId)는 노출하지 않는다", example = "보기 2번에 오타가 있습니다.")
        String content,

        @Schema(description = "처리 여부", example = "false")
        @JsonProperty("isResolved")
        boolean isResolved,

        @Schema(description = "신고 시각 (KST, 오프셋 없음)", example = "2026-11-18T10:15:30.123456")
        LocalDateTime submittedAt
) {

    public static ReportDetailResponse of(
            long reportId,
            ReportType reportType,
            long problemId,
            String content,
            boolean isResolved,
            LocalDateTime submittedAt
    ) {
        return ReportDetailResponse.builder()
                .reportId(reportId)
                .reportType(reportType)
                .problemId(problemId)
                .content(content)
                .isResolved(isResolved)
                .submittedAt(submittedAt)
                .build();
    }
}
