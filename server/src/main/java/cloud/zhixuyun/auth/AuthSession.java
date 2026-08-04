package cloud.zhixuyun.auth;

import java.time.Instant;

public class AuthSession {
    private final String token;
    private final Long userId;
    private final Instant expiresAt;

    public AuthSession(String token, Long userId, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
}
