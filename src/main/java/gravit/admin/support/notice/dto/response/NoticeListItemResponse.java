package gravit.admin.support.notice.dto.response;

import gravit.admin.support.notice.domain.NoticeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder(access = AccessLevel.PRIVATE)
public record NoticeListItemResponse(

        @Schema(description = "공지 ID", example = "3")
        long noticeId,

        @Schema(description = "제목", example = "11월 점검 안내")
        String title,

        @Schema(description = "상태", example = "PUBLISHED")
        NoticeStatus status,

        @Schema(description = "상단 고정 여부", example = "false")
        boolean pinned,

        @Schema(description = "게시 시각 (KST, 오프셋 없음). 게시 전이면 null", example = "2026-11-18T10:15:30.123456")
        LocalDateTime publishedAt,

        @Schema(description = "작성 시각 (KST, 오프셋 없음)", example = "2026-11-18T09:00:00")
        LocalDateTime createdAt
) {

    public static NoticeListItemResponse of(
            long noticeId,
            String title,
            NoticeStatus status,
            boolean pinned,
            LocalDateTime publishedAt,
            LocalDateTime createdAt
    ) {
        return NoticeListItemResponse.builder()
                .noticeId(noticeId)
                .title(title)
                .status(status)
                .pinned(pinned)
                .publishedAt(publishedAt)
                .createdAt(createdAt)
                .build();
    }
}
