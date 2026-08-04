package cloud.zhixuyun.auth;

public record CurrentUserResponse(
        Long id,
        String loginName,
        String displayName,
        Role role,
        boolean enabled,
        boolean mustChangePassword
) {
    public static CurrentUserResponse from(UserAccount user) {
        String displayName = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getLoginName()
                : user.getDisplayName();
        return new CurrentUserResponse(user.getId(), user.getLoginName(), displayName, user.getRole(), user.isEnabled(), user.isMustChangePassword());
    }
}
