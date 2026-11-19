package gravit.admin.auth.support;

import gravit.admin.auth.infrastructure.AdminMeClient;
import gravit.admin.auth.principal.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminVerifier {

    private final AdminMeClient adminMeClient;
    private final AdminVerificationCache adminVerificationCache;

    public LoginAdmin verify(String accessToken) {
        return adminVerificationCache.get(accessToken)
                .orElseGet(() -> fetchAndCache(accessToken));
    }

    private LoginAdmin fetchAndCache(String accessToken) {
        LoginAdmin admin = adminMeClient.fetch(accessToken);
        adminVerificationCache.put(accessToken, admin);
        return admin;
    }
}
