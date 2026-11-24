package gravit.admin.auth.support;

import gravit.admin.auth.exception.AdminAuthException;
import gravit.admin.auth.infrastructure.AdminMeClient;
import gravit.admin.auth.principal.LoginAdmin;
import gravit.admin.global.exception.domain.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

import static gravit.admin.global.exception.domain.CustomErrorCode.NOT_ADMIN;
import static gravit.admin.global.exception.domain.CustomErrorCode.TOKEN_INVALID;

@Component
@RequiredArgsConstructor
public class AdminVerifier {

    private static final Set<ErrorCode> REJECTION_ERROR_CODES = Set.of(TOKEN_INVALID, NOT_ADMIN);

    private final AdminMeClient adminMeClient;
    private final AdminVerificationCache adminVerificationCache;

    public LoginAdmin verify(String accessToken) {
        return adminVerificationCache.get(accessToken)
                .orElseGet(() -> fetchAndCache(accessToken));
    }

    public LoginAdmin verifyWithoutCache(String accessToken) {
        return fetchAndCache(accessToken);
    }

    private LoginAdmin fetchAndCache(String accessToken) {
        LoginAdmin admin;
        try {
            admin = adminMeClient.fetch(accessToken);
        } catch (AdminAuthException e) {
            if (REJECTION_ERROR_CODES.contains(e.getErrorCode())) {
                adminVerificationCache.evict(accessToken);
            }
            throw e;
        }

        adminVerificationCache.put(accessToken, admin);
        return admin;
    }
}
