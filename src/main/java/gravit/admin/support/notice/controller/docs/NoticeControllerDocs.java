package gravit.admin.support.notice.controller.docs;

import gravit.admin.global.dto.response.PageResponse;
import gravit.admin.support.notice.dto.response.NoticeDetailResponse;
import gravit.admin.support.notice.dto.response.NoticeListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Notice API", description = "백오피스 공지 관리")
public interface NoticeControllerDocs {

    @Operation(summary = "공지 목록", description = "삭제한 공지 제외. 상단 고정, 게시일 최신순(미게시는 뒤), 작성일 최신순. 한 페이지 20건.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "page 가 1보다 작음(PAGE_4001), 파라미터 형식 오류(GLOBAL_4001)")
    })
    ResponseEntity<PageResponse<NoticeListItemResponse>> getNotices(@Parameter(description = "페이지 번호 (1부터)", example = "1") int page);

    @Operation(summary = "공지 상세")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제한 공지(NOTICE_4041)")
    })
    ResponseEntity<NoticeDetailResponse> getNotice(@Parameter(description = "공지 ID") Long noticeId);
}
