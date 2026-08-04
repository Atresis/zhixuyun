package cloud.zhixuyun.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class AuthSessionRepository {
    private final JdbcTemplate jdbc;

    public AuthSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AuthSession save(AuthSession session) {
        int updated = jdbc.update("update auth_session set user_id=?, expires_at=? where token=?",
                session.getUserId(), Timestamp.from(session.getExpiresAt()), session.getToken());
        if (updated == 0) {
            jdbc.update("insert into auth_session(token, user_id, expires_at) values (?, ?, ?)",
                    session.getToken(), session.getUserId(), Timestamp.from(session.getExpiresAt()));
        }
        return session;
    }

    public Optional<AuthSession> findByToken(String token) {
        return jdbc.query("select token, user_id, expires_at from auth_session where token = ?",
                (rs, row) -> new AuthSession(rs.getString("token"), rs.getLong("user_id"), rs.getTimestamp("expires_at").toInstant()), token)
                .stream().findFirst();
    }

    public void deleteByToken(String token) {
        jdbc.update("delete from auth_session where token = ?", token);
    }

    public void deleteByUserId(Long userId) {
        jdbc.update("delete from auth_session where user_id = ?", userId);
    }

    public void clear() {
        jdbc.update("delete from auth_session");
    }
}
