package gravit.admin.adapter.gravit.infrastructure;

import gravit.admin.adapter.gravit.config.GravitProps;
import gravit.admin.adapter.gravit.dto.internal.GravitRequestDto;
import gravit.admin.adapter.gravit.dto.internal.GravitResponseDto;
import gravit.admin.adapter.support.HttpRequestFactories;
import gravit.admin.global.exception.domain.RestApiException;
import gravit.admin.global.security.AdminVerificationCacheEvictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.Set;

import static gravit.admin.global.exception.domain.CustomErrorCode.MAIN_SERVER_RESULT_UNKNOWN;
import static gravit.admin.global.exception.domain.CustomErrorCode.MAIN_SERVER_UNREACHABLE;

@Slf4j
@Component
public class GravitClient {

    public static final String ADMIN_ME_PATH = "/api/v1/admin/me";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String QUERY_DELIMITER = "?";
    private static final char PATH_DELIMITER = '/';
    private static final int ADMIN_ME_MAX_ATTEMPTS = 2;
    private static final Set<Integer> REJECTION_STATUSES = Set.of(401, 403);
    private static final List<Class<? extends Throwable>> NOT_SENT_FAILURES = List.of(
            ConnectException.class,
            HttpConnectTimeoutException.class,
            UnknownHostException.class,
            UnresolvedAddressException.class,
            SSLHandshakeException.class
    );

    private final RestClient restClient;

    private final AdminVerificationCacheEvictor adminVerificationCacheEvictor;

    private final String host;

    public GravitClient(
            RestClient.Builder restClientBuilder,
            AdminVerificationCacheEvictor adminVerificationCacheEvictor,
            GravitProps gravitProps
    ) {
        this.restClient = restClientBuilder
                .requestFactory(HttpRequestFactories.jdk(gravitProps.connectTimeout(), gravitProps.readTimeout()))
                .build();
        this.adminVerificationCacheEvictor = adminVerificationCacheEvictor;
        this.host = StringUtils.trimTrailingCharacter(gravitProps.host(), PATH_DELIMITER);
    }

    public GravitResponseDto exchange(GravitRequestDto request) {
        String accessToken = currentAccessToken();

        GravitResponseDto response = send(request, accessToken);

        if (REJECTION_STATUSES.contains(response.status())) {
            adminVerificationCacheEvictor.evict(accessToken);
        }
        return response;
    }

    private GravitResponseDto send(
            GravitRequestDto request,
            String accessToken
    ) {
        int maxAttempts = isAdminMe(request) ? ADMIN_ME_MAX_ATTEMPTS : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return call(request, accessToken);
            } catch (ResourceAccessException e) {
                boolean notSent = isNotSent(e);
                if (!notSent || attempt >= maxAttempts) {
                    log.warn("메인 서버 호출 실패. method={}, path={}, cause={}", request.method(), request.path(), rootCauseName(e));
                    throw new RestApiException(notSent ? MAIN_SERVER_UNREACHABLE : MAIN_SERVER_RESULT_UNKNOWN);
                }
                log.warn("메인 서버 연결 실패, 다시 시도합니다. method={}, path={}, cause={}", request.method(), request.path(), rootCauseName(e));
            }
        }
    }

    private GravitResponseDto call(
            GravitRequestDto request,
            String accessToken
    ) {
        RestClient.RequestBodySpec spec = restClient.method(request.method())
                .uri(toUri(request))
                .headers(headers -> {
                    headers.set(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken);
                    if (request.contentType() != null) {
                        headers.set(HttpHeaders.CONTENT_TYPE, request.contentType());
                    }
                    if (request.accept() != null) {
                        headers.set(HttpHeaders.ACCEPT, request.accept());
                    }
                });
        if (request.hasBody()) {
            spec.headers(headers -> headers.setContentLength(request.body().length))
                    .body(outputStream -> outputStream.write(request.body()));
        }

        return spec.exchange((httpRequest, response) -> GravitResponseDto.of(
                response.getStatusCode().value(),
                response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                response.getBody().readAllBytes()
        ));
    }

    private URI toUri(GravitRequestDto request) {
        String query = request.query() == null ? "" : QUERY_DELIMITER + request.query();
        return URI.create(host + request.path() + query);
    }

    private boolean isAdminMe(GravitRequestDto request) {
        return request.method() == HttpMethod.GET && ADMIN_ME_PATH.equals(request.path());
    }

    private boolean isNotSent(ResourceAccessException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            Throwable current = cause;
            if (NOT_SENT_FAILURES.stream().anyMatch(type -> type.isInstance(current))) {
                return true;
            }
        }
        return false;
    }

    private String rootCauseName(ResourceAccessException e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private String currentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getCredentials() instanceof String accessToken) || accessToken.isBlank()) {
            throw new IllegalStateException("메인 서버에 붙일 관리자 토큰이 없습니다.");
        }
        return accessToken;
    }
}
