package gravit.admin.auth.support;

import gravit.admin.auth.config.AuthProps;
import gravit.admin.auth.principal.LoginAdmin;
import gravit.admin.global.security.AdminVerificationCacheEvictor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminVerificationCache implements AdminVerificationCacheEvictor {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int MAX_ENTRIES = 10_000;

    private final Map<String, CachedAdmin> keyToAdmin = new ConcurrentHashMap<>();

    private final AccessTokenExpiryReader accessTokenExpiryReader;

    private final Clock clock;
    private final Duration ttl;

    public AdminVerificationCache(
            AccessTokenExpiryReader accessTokenExpiryReader,
            Clock clock,
            AuthProps authProps
    ) {
        this.accessTokenExpiryReader = accessTokenExpiryReader;
        this.clock = clock;
        this.ttl = authProps.cacheTtl();
    }

    public Optional<LoginAdmin> get(String accessToken) {
        Instant now = clock.instant();
        String key = hash(accessToken);

        CachedAdmin cached = keyToAdmin.get(key);
        if (cached == null) {
            return Optional.empty();
        }
        if (!cached.expiresAt().isAfter(now)) {
            keyToAdmin.remove(key, cached);
            return Optional.empty();
        }
        return Optional.of(cached.admin());
    }

    public void put(
            String accessToken,
            LoginAdmin admin
    ) {
        Instant now = clock.instant();
        Instant expiresAt = expiresAt(accessToken, now);
        if (!expiresAt.isAfter(now)) {
            return;
        }

        evictExpired(now);
        if (keyToAdmin.size() >= MAX_ENTRIES) {
            keyToAdmin.clear();
        }
        keyToAdmin.put(hash(accessToken), new CachedAdmin(admin, expiresAt));
    }

    @Override
    public void evict(String accessToken) {
        keyToAdmin.remove(hash(accessToken));
    }

    public void clear() {
        keyToAdmin.clear();
    }

    private Instant expiresAt(
            String accessToken,
            Instant now
    ) {
        Instant ttlExpiresAt = now.plus(ttl);
        return accessTokenExpiryReader.read(accessToken)
                .filter(tokenExpiresAt -> tokenExpiresAt.isBefore(ttlExpiresAt))
                .orElse(ttlExpiresAt);
    }

    private void evictExpired(Instant now) {
        keyToAdmin.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String hash(String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(accessToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record CachedAdmin(
            LoginAdmin admin,

            Instant expiresAt
    ) {
    }
}
