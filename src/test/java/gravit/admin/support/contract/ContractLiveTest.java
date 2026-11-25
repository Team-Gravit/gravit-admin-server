package gravit.admin.support.contract;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * prod 메인과 관리자 서버의 GET 응답을 비교하는 계약 테스트(설계 9-2). ./gradlew liveTest에서만 돌고,
 * 세 환경 변수 중 하나라도 없으면 건너뛴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("live")
@EnabledIfEnvironmentVariable(named = LiveContractClient.MAIN_BASE_ENV, matches = ".+")
@EnabledIfEnvironmentVariable(named = LiveContractClient.ADMIN_BASE_ENV, matches = ".+")
@EnabledIfEnvironmentVariable(named = LiveContractClient.TOKEN_ENV, matches = ".+")
public @interface ContractLiveTest {
}
