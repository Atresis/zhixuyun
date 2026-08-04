package cloud.zhixuyun.auth;

public record ProfileResponse(
        Long id,
        String loginName,
        String displayName,
        String email,
        String phone,
        String bio,
        Role role,
        boolean enabled,
        boolean mustChangePassword,
        boolean hasAvatar
) {
    public static ProfileResponse from(UserAccount user) {
        String displayName = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getLoginName()
                : user.getDisplayName();
        return new ProfileResponse(
                user.getId(),
                user.getLoginName(),
                displayName,
                user.getEmail() == null ? "" : user.getEmail(),
                user.getPhone() == null ? "" : user.getPhone(),
                user.getBio() == null ? "" : user.getBio(),
                user.getRole(),
                user.isEnabled(),
                user.isMustChangePassword(),
                user.getAvatarContent() != null && user.getAvatarContentType() != null
        );
    }
}
