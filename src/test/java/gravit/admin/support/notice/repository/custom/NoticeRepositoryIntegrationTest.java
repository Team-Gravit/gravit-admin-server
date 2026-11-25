package gravit.admin.support.notice.repository.custom;

import gravit.admin.support.QaSpringBootTest;
import gravit.admin.support.notice.domain.NoticeStatus;
import gravit.admin.support.notice.dto.response.NoticeDetailResponse;
import gravit.admin.support.notice.dto.response.NoticeListItemResponse;
import gravit.admin.support.notice.fixture.NoticeMainDbFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static gravit.admin.support.notice.fixture.NoticeMainDbFixture.BASE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@QaSpringBootTest
@Import(NoticeMainDbFixture.class)
class NoticeRepositoryIntegrationTest {

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private NoticeMainDbFixture fixture;

    @Nested
    @DisplayName("목록을 조회할 때")
    class Page {

        @Test
        void 핀_게시일_최신순_다음_비핀_게시일_최신순이고_미게시는_작성일_최신순으로_맨_뒤다() {
            // given
            fixture.게시된_공지("일반-오래됨", false, BASE_TIME.plusHours(1));
            fixture.임시저장_공지("임시-오래됨", BASE_TIME.plusHours(2));
            fixture.게시된_공지("핀-오래됨", true, BASE_TIME.plusHours(3));
            fixture.게시된_공지("일반-최신", false, BASE_TIME.plusHours(4));
            fixture.임시저장_공지("임시-최신", BASE_TIME.plusHours(5));
            fixture.게시된_공지("핀-최신", true, BASE_TIME.plusHours(6));

            // when
            List<NoticeListItemResponse> page = noticeRepository.findPage(0, 20);

            // then
            assertThat(page).extracting(NoticeListItemResponse::title)
                    .containsExactly("핀-최신", "핀-오래됨", "일반-최신", "일반-오래됨", "임시-최신", "임시-오래됨");
        }

        @Test
        void 삭제한_공지는_목록과_개수에서_뺀다() {
            // given
            fixture.게시된_공지("남은 공지", false, BASE_TIME);
            fixture.삭제된_공지("지운 공지");

            // when
            List<NoticeListItemResponse> page = noticeRepository.findPage(0, 20);

            // then
            assertSoftly(softly -> {
                softly.assertThat(page).extracting(NoticeListItemResponse::title).containsExactly("남은 공지");
                softly.assertThat(noticeRepository.count()).isEqualTo(1);
            });
        }

        @Test
        void 목록_항목은_요약과_본문_없이_돌려준다() {
            // given
            fixture.임시저장_공지("임시", BASE_TIME);

            // when
            NoticeListItemResponse item = noticeRepository.findPage(0, 20).getFirst();

            // then
            assertThat(item).isEqualTo(NoticeListItemResponse.of(1L, "임시", NoticeStatus.DRAFT, false, null, BASE_TIME));
        }
    }

    @Nested
    @DisplayName("상세를 조회할 때")
    class Detail {

        @Test
        void 있으면_요약과_본문까지_돌려준다() {
            // given
            fixture.게시된_공지("점검 안내", true, BASE_TIME);

            // when & then
            assertThat(noticeRepository.findDetailById(1L)).contains(NoticeDetailResponse.of(
                    1L, "점검 안내", "점검 안내 요약", "점검 안내 본문", NoticeStatus.PUBLISHED, true, BASE_TIME, BASE_TIME));
        }

        @Test
        void 삭제한_공지는_없는_것으로_본다() {
            // given
            fixture.삭제된_공지("지운 공지");

            // when & then
            assertThat(noticeRepository.findDetailById(1L)).isEmpty();
        }
    }
}
