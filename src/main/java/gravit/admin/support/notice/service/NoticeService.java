package gravit.admin.support.notice.service;

import gravit.admin.global.dto.response.PageResponse;
import gravit.admin.global.exception.domain.RestApiException;
import gravit.admin.global.util.AdminPages;
import gravit.admin.support.notice.dto.response.NoticeDetailResponse;
import gravit.admin.support.notice.dto.response.NoticeListItemResponse;
import gravit.admin.support.notice.repository.custom.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static gravit.admin.global.exception.domain.CustomErrorCode.NOTICE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public PageResponse<NoticeListItemResponse> getNotices(int page) {
        long offset = AdminPages.offsetOf(page);

        List<NoticeListItemResponse> contents = noticeRepository.findPage(offset, AdminPages.PAGE_SIZE);
        long totalElements = noticeRepository.count();

        return PageResponse.of(page, AdminPages.PAGE_SIZE, totalElements, contents);
    }

    public NoticeDetailResponse getNotice(long noticeId) {
        return noticeRepository.findDetailById(noticeId)
                .orElseThrow(() -> new RestApiException(NOTICE_NOT_FOUND));
    }
}
