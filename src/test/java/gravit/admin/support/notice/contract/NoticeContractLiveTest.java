package gravit.admin.support.notice.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.admin.support.contract.ContractComparator;
import gravit.admin.support.contract.ContractLiveTest;
import gravit.admin.support.contract.ContractResponse;
import gravit.admin.support.contract.LiveContractClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * NTC-01, NTC-02 전환 조건(설계 10-2 2단계). 관리자 서버에는 X-Admin-Route: local을 붙여 새 구현과 prod 메인을 비교한다.
 * 목록 정렬은 id로 동점을 가르지 않는다(설계 M-8). 게시일, 작성일이 마이크로초까지 같은 공지가 페이지 경계에 걸리면
 * 두 서버의 순서가 달라질 수 있으니, 차이가 그 경우뿐인지 확인한 뒤 판단한다.
 * NTC-03~05(생성, 수정, 삭제)는 메인에 남는 쓰기 API라 비교하지 않는다.
 */
@ContractLiveTest
class NoticeContractLiveTest {

    private static final String NOTICES_PATH = "/api/v1/admin/notices";

    private final LiveContractClient client = LiveContractClient.fromEnv();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            NOTICES_PATH,
            NOTICES_PATH + "?page=1",
            NOTICES_PATH + "?page=2",
            NOTICES_PATH + "?page=9999",
            NOTICES_PATH + "?page=0",
            NOTICES_PATH + "?page=abc",
            NOTICES_PATH + "/999999999",
            NOTICES_PATH + "/abc"
    })
    void 새_구현의_응답은_prod_메인과_같다(String pathAndQuery) {
        // when
        List<String> differences = compare(pathAndQuery);

        // then
        assertThat(differences).as("main과 admin 응답 차이").isEmpty();
    }

    @Test
    void 실제_공지_상세도_prod_메인과_같다() throws IOException {
        // given
        JsonNode contents = new ObjectMapper().readTree(client.getMain(NOTICES_PATH).body()).path("contents");
        assumeTrue(contents.isArray() && !contents.isEmpty(), "prod에 공지가 없습니다.");
        long noticeId = contents.get(0).path("noticeId").asLong();

        // when
        List<String> differences = compare(NOTICES_PATH + "/" + noticeId);

        // then
        assertThat(differences).as("main과 admin 응답 차이").isEmpty();
    }

    private List<String> compare(String pathAndQuery) {
        ContractResponse main = client.getMain(pathAndQuery);
        ContractResponse admin = client.getAdmin(pathAndQuery);
        return ContractComparator.compare(main, admin);
    }
}
