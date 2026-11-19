package gravit.admin.auth.principal;

import gravit.admin.auth.dto.internal.AdminMeDto;

public record LoginAdmin(
        long adminId,
        String nickname,
        String email
) {

    public static LoginAdmin from(AdminMeDto adminMe) {
        return new LoginAdmin(
                adminMe.adminId(),
                adminMe.nickname(),
                adminMe.email()
        );
    }
}
