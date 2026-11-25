package gravit.admin.insight.dto.internal;

import java.time.LocalDate;

public record DailyActiveUserCountDto(
        LocalDate activityDate,
        long activeUserCount
) {
}
