package cloud.zhixuyun.auth;

public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
