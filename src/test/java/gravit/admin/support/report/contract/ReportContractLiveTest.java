package gravit.admin.support.report.contract;

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
 * RPT-01, RPT-02 전환 조건(설계 10-2 2단계). 관리자 서버에는 X-Admin-Route: local을 붙여 새 구현과 prod 메인을 비교한다.
 * RPT-03(상태 변경)은 메인에 남는 쓰기 API라 비교하지 않는다.
 */
@ContractLiveTest
class ReportContractLiveTest {

    private static final String REPORTS_PATH = "/api/v1/admin/reports";

    private final LiveContractClient client = LiveContractClient.fromEnv();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            REPORTS_PATH,
            REPORTS_PATH + "?page=1",
            REPORTS_PATH + "?page=2",
            REPORTS_PATH + "?page=9999",
            REPORTS_PATH + "?isResolved=false",
            REPORTS_PATH + "?isResolved=true",
            REPORTS_PATH + "?reportType=TYPO_ERROR",
            REPORTS_PATH + "?reportType=CONTENT_ERROR&isResolved=false",
            REPORTS_PATH + "?page=0",
            REPORTS_PATH + "?page=abc",
            REPORTS_PATH + "?reportType=typo_error",
            REPORTS_PATH + "?isResolved=maybe",
            REPORTS_PATH + "/999999999",
            REPORTS_PATH + "/abc"
    })
    void 새_구현의_응답은_prod_메인과_같다(String pathAndQuery) {
        // when
        List<String> differences = compare(pathAndQuery);

        // then
        assertThat(differences).as("main과 admin 응답 차이").isEmpty();
    }

    @Test
    void 실제_신고_상세도_prod_메인과_같다() throws IOException {
        // given
        JsonNode contents = new ObjectMapper().readTree(client.getMain(REPORTS_PATH).body()).path("contents");
        assumeTrue(contents.isArray() && !contents.isEmpty(), "prod에 신고가 없습니다.");
        long reportId = contents.get(0).path("reportId").asLong();

        // when
        List<String> differences = compare(REPORTS_PATH + "/" + reportId);

        // then
        assertThat(differences).as("main과 admin 응답 차이").isEmpty();
    }

    private List<String> compare(String pathAndQuery) {
        ContractResponse main = client.getMain(pathAndQuery);
        ContractResponse admin = client.getAdmin(pathAndQuery);
        return ContractComparator.compare(main, admin);
    }
}
