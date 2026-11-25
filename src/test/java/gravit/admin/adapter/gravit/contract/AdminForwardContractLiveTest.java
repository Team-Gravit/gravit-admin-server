package gravit.admin.adapter.gravit.contract;

import gravit.admin.support.contract.ContractComparator;
import gravit.admin.support.contract.ContractLiveTest;
import gravit.admin.support.contract.ContractResponse;
import gravit.admin.support.contract.LiveContractClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 서버가 아직 직접 처리하지 않는 GET을 전달 계층이 메인 응답 그대로 돌려주는지 prod에서 비교한다.
 * 어떤 도메인을 옮기면(헤더 조건을 단 구현이 생기면) 그 도메인의 경로는 여기서 빼고 도메인 계약 테스트로 옮긴다.
 */
@ContractLiveTest
class AdminForwardContractLiveTest {

    private final LiveContractClient client = LiveContractClient.fromEnv();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/v1/admin/me",
            "/api/v1/admin/dashboard/summary",
            "/api/v1/admin/dashboard/active-users/daily?days=7",
            "/api/v1/admin/reports",
            "/api/v1/admin/reports?page=1&isResolved=false",
            "/api/v1/admin/reports?page=0",
            "/api/v1/admin/reports/999999999"
    })
    void 전달되는_GET은_메인_응답과_같다(String pathAndQuery) {
        // given
        ContractResponse main = client.getMain(pathAndQuery);
        ContractResponse admin = client.getAdmin(pathAndQuery);

        // when
        List<String> differences = ContractComparator.compare(main, admin);

        // then
        assertThat(differences).as("main과 admin 응답 차이").isEmpty();
    }
}
