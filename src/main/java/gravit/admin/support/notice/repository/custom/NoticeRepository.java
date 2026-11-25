package gravit.admin.support.notice.repository.custom;

import gravit.admin.support.notice.dto.response.NoticeDetailResponse;
import gravit.admin.support.notice.dto.response.NoticeListItemResponse;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository {

    List<NoticeListItemResponse> findPage(
            long offset,
            int limit
    );

    long count();

    Optional<NoticeDetailResponse> findDetailById(long noticeId);
}
