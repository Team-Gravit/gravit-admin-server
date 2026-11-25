package gravit.admin.insight.contract;

import gravit.admin.support.contract.ContractComparator;
import gravit.admin.support.contract.ContractLiveTest;
import gravit.admin.support.contract.ContractResponse;
import gravit.admin.support.contract.LiveContractClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DASH-01~03 전환 조건(설계 10-2 2단계). 관리자 서버에는 X-Admin-Route: local을 붙여 새 구현과 prod 메인을 비교한다.
 * 400의 검증 메시지는 JVM 로케일을 따르므로 두 서버 이미지(eclipse-temurin:21-jre)가 같은 문구를 내는지도 함께 본다(설계 9-3).
 */
@ContractLiveTest
class DashboardContractLiveTest {

    private final LiveContractClient client = LiveContractClient.fromEnv();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/v1/admin/dashboard/summary",
            "/api/v1/admin/dashboard/active-users/daily",
            "/api/v1/admin/dashboard/active-users/daily?days=1",
            "/api/v1/admin/dashboard/active-users/daily?days=365",
            "/api/v1/admin/dashboard/active-users/daily?days=0",
            "/api/v1/admin/dashboard/active-users/daily?days=366",
            "/api/v1/admin/dashboard/active-users/daily?days=abc",
            "/api/v1/admin/dashboard/active-users/monthly",
            "/api/v1/admin/dashboard/active-users/monthly?months=1",
            "/api/v1/admin/dashboard/active-users/monthly?months=36",
            "/api/v1/admin/dashboard/active-users/monthly?months=0",
            "/api/v1/admin/dashboard/active-users/monthly?months=37"
    })
    void 새_구현의_응답은_prod_메인과_같다(String pathAndQuery) {
        // given
        ContractResponse main = client.getMain(pathAndQuery);
        ContractResponse admin = client.getAdmin(pathAndQuery);

        // when
        List<String> differences = ContractComparator.compare(main, admin);

        // then
        assertThat(differences).as("main과 admin 응답 차이").isEmpty();
    }
}
