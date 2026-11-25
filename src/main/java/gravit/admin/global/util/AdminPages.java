package gravit.admin.global.util;

import gravit.admin.global.exception.domain.RestApiException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static gravit.admin.global.exception.domain.CustomErrorCode.PAGE_MUST_START_FROM_1;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminPages {

    public static final int PAGE_SIZE = 20;

    public static long offsetOf(int page) {
        if (page < 1) {
            throw new RestApiException(PAGE_MUST_START_FROM_1);
        }
        return (long) (page - 1) * PAGE_SIZE;
    }
}
