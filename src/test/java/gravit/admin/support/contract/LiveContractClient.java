package gravit.admin.support.contract;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 같은 GET 요청을 prod 메인과 관리자 서버에 보낸다. GET만 보낸다(prod에 쓰지 않는다).
 * 관리자 서버 요청에는 X-Admin-Route: local을 붙여, 헤더 조건을 단 새 구현이 받게 한다(설계 5-7).
 * 토큰은 요청 헤더에만 쓰고 toString, 예외 메시지에 넣지 않는다.
 */
public final class LiveContractClient {

    public static final String MAIN_BASE_ENV = "ADMIN_CONTRACT_MAIN_BASE";
    public static final String ADMIN_BASE_ENV = "ADMIN_CONTRACT_ADMIN_BASE";
    public static final String TOKEN_ENV = "ADMIN_CONTRACT_TOKEN";
    public static final String ROUTE_HEADER = "X-Admin-Route";
    public static final String ROUTE_LOCAL = "local";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final String mainBase;
    private final String adminBase;
    private final String token;

    private LiveContractClient(
            String mainBase,
            String adminBase,
            String token
    ) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.mainBase = trimTrailingSlash(mainBase);
        this.adminBase = trimTrailingSlash(adminBase);
        this.token = token;
    }

    public static LiveContractClient fromEnv() {
        return new LiveContractClient(System.getenv(MAIN_BASE_ENV), System.getenv(ADMIN_BASE_ENV), System.getenv(TOKEN_ENV));
    }

    public ContractResponse getMain(String pathAndQuery) {
        return get(mainBase, pathAndQuery, false);
    }

    public ContractResponse getAdmin(String pathAndQuery) {
        return get(adminBase, pathAndQuery, true);
    }

    private ContractResponse get(
            String base,
            String pathAndQuery,
            boolean localRoute
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + pathAndQuery))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", BEARER_PREFIX + token)
                .header("Accept", "application/json")
                .GET();
        if (localRoute) {
            builder.header(ROUTE_HEADER, ROUTE_LOCAL);
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return ContractResponse.of(
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(null),
                    response.body()
            );
        } catch (IOException e) {
            throw new IllegalStateException("계약 테스트 요청 실패: " + base + pathAndQuery + " (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("계약 테스트 요청이 중단됐습니다: " + base + pathAndQuery);
        }
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public String toString() {
        return "LiveContractClient[mainBase=" + mainBase + ", adminBase=" + adminBase + "]";
    }
}
