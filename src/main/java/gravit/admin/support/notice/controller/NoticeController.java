package gravit.admin.support.notice.controller;

import gravit.admin.global.dto.response.PageResponse;
import gravit.admin.support.notice.controller.docs.NoticeControllerDocs;
import gravit.admin.support.notice.dto.response.NoticeDetailResponse;
import gravit.admin.support.notice.dto.response.NoticeListItemResponse;
import gravit.admin.support.notice.service.NoticeService;
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
@RequestMapping("/api/v1/admin/notices")
public class NoticeController implements NoticeControllerDocs {

    private static final String LOCAL_ROUTE = "X-Admin-Route=local";

    private final NoticeService noticeService;

    @GetMapping(headers = LOCAL_ROUTE)
    public ResponseEntity<PageResponse<NoticeListItemResponse>> getNotices(@RequestParam(value = "page", defaultValue = "1") int page) {
        return ResponseEntity.status(OK).body(noticeService.getNotices(page));
    }

    @GetMapping(value = "/{noticeId}", headers = LOCAL_ROUTE)
    public ResponseEntity<NoticeDetailResponse> getNotice(@PathVariable("noticeId") Long noticeId) {
        return ResponseEntity.status(OK).body(noticeService.getNotice(noticeId));
    }
}
