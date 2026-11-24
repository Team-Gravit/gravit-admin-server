package gravit.admin.auth.fixture;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class AccessTokenFixture {

    public static final String SIGNATURE = "c2lnbmF0dXJlLW9mLWdyYXZpdC1hZG1pbi10ZXN0";

    private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

    public static String 만료_시각을_담은_토큰(Instant expiresAt) {
        return 토큰("{\"sub\":\"7\",\"exp\":" + expiresAt.getEpochSecond() + "}");
    }

    public static String 만료_시각이_없는_토큰() {
        return 토큰("{\"sub\":\"7\"}");
    }

    public static String 본문이_JSON이_아닌_토큰() {
        return 토큰("not-json");
    }

    public static String 만료_시각이_숫자가_아닌_토큰() {
        return 토큰("{\"sub\":\"7\",\"exp\":\"soon\"}");
    }

    public static String 본문이_객체가_아닌_토큰() {
        return 토큰("[1700000000]");
    }

    public static String 범위를_넘는_만료_시각을_담은_토큰() {
        return 토큰("{\"sub\":\"7\",\"exp\":1e300}");
    }

    public static String 본문이_base64url이_아닌_토큰() {
        return HEADER + ".%%%." + SIGNATURE;
    }

    public static String 마침표가_없는_토큰() {
        return "opaque-admin-access-token";
    }

    public static String 조각이_다섯인_토큰() {
        return HEADER + "." + encode("{\"exp\":1}") + ".key.iv." + SIGNATURE;
    }

    private static String 토큰(String payload) {
        return HEADER + "." + encode(payload) + "." + SIGNATURE;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
