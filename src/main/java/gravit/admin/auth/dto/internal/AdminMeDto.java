package gravit.admin.auth.dto.internal;

public record AdminMeDto(
        Long adminId,
        String nickname,
        String email,
        Integer profileImgNumber
) {
}
