package gravit.admin.global.security;

public interface AdminVerificationCacheEvictor {

    void evict(String accessToken);
}
