package gravit.admin.insight.dto.internal;

public record MonthlyActiveUserCountDto(
        int year,
        int month,
        long activeUserCount
) {
}
