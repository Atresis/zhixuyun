package cloud.zhixuyun.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthSessionService {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AuthSessionService(
            AuthSessionRepository sessions,
            UserRepository users,
            @Value("${zhixuyun.auth.session-ttl:12h}") Duration ttl
    ) {
        this(sessions, users, ttl, Clock.systemUTC());
    }

    AuthSessionService(AuthSessionRepository sessions, UserRepository users, Duration ttl, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.ttl = ttl;
        this.clock = clock;
    }

    public AuthSession create(UserAccount user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return sessions.save(new AuthSession(token, user.getId(), now().plus(ttl)));
    }

    public UserAccount requireUser(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        AuthSession session = sessions.findByToken(token)
                .orElseThrow(() -> unauthorized("SESSION_EXPIRED", "登录会话无效或已过期"));
        if (session.expired(now())) {
            sessions.deleteByToken(token);
            throw unauthorized("SESSION_EXPIRED", "登录会话无效或已过期");
        }
        UserAccount user = users.findById(session.getUserId())
                .orElseThrow(() -> unauthorized("SESSION_EXPIRED", "登录会话无效或已过期"));
        if (!user.isEnabled()) {
            sessions.deleteByToken(token);
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已被禁用");
        }
        return user;
    }

    public void revoke(String authorizationHeader) {
        String token = bearerTokenOrNull(authorizationHeader);
        if (token != null) sessions.deleteByToken(token);
    }

    public void revokeAll(Long userId) {
        sessions.deleteByUserId(userId);
    }

    static String bearerToken(String authorizationHeader) {
        String token = bearerTokenOrNull(authorizationHeader);
        if (token == null) throw unauthorized("UNAUTHORIZED", "需要登录后才能访问");
        return token;
    }

    private static String bearerTokenOrNull(String authorizationHeader) {
        if (authorizationHeader == null) return null;
        String value = authorizationHeader.trim();
        if (value.length() <= 7 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = value.substring(7).trim();
        return token.isBlank() ? null : token;
    }

    private Instant now() { return clock.instant(); }

    private static AuthException unauthorized(String code, String message) {
        return new AuthException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
