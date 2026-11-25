package gravit.admin.insight.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;

@Builder(access = AccessLevel.PRIVATE)
public record DashboardSummaryResponse(

        @Schema(
                description = "탈퇴(DELETED)를 뺀 전체 회원 수",
                example = "1200",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long totalUsers,

        @Schema(
                description = "검수 대기(PENDING) 스테이징 라벨 수",
                example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long pendingLabelsCount,

        @Schema(
                description = "미처리 신고 수",
                example = "5",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        long unresolvedReportsCount
) {

    public static DashboardSummaryResponse of(
            long totalUsers,
            long pendingLabelsCount,
            long unresolvedReportsCount
    ) {
        return DashboardSummaryResponse.builder()
                .totalUsers(totalUsers)
                .pendingLabelsCount(pendingLabelsCount)
                .unresolvedReportsCount(unresolvedReportsCount)
                .build();
    }
}
