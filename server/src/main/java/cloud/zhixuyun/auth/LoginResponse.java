package cloud.zhixuyun.auth;

public record LoginResponse(String token, CurrentUserResponse user) {
}
