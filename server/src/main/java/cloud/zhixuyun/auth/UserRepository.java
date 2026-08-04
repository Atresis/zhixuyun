package cloud.zhixuyun.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByLoginName(String loginName) {
        return jdbc.query("select id, login_name, password_hash, display_name, role, enabled, email, phone, bio, must_change_password, failed_login_attempts, avatar_content_type, avatar_content from user_account where lower(login_name) = lower(?)",
                (rs, row) -> map(rs.getLong("id"), rs.getString("login_name"), rs.getString("password_hash"),
                        rs.getString("display_name"), rs.getString("role"), rs.getBoolean("enabled"),
                        rs.getString("email"), rs.getString("phone"), rs.getString("bio"),
                        rs.getBoolean("must_change_password"), rs.getInt("failed_login_attempts"),
                        rs.getString("avatar_content_type"), rs.getBytes("avatar_content")), loginName).stream().findFirst();
    }

    public Optional<UserAccount> findById(Long id) {
        return jdbc.query("select id, login_name, password_hash, display_name, role, enabled, email, phone, bio, must_change_password, failed_login_attempts, avatar_content_type, avatar_content from user_account where id = ?",
                (rs, row) -> map(rs.getLong("id"), rs.getString("login_name"), rs.getString("password_hash"),
                        rs.getString("display_name"), rs.getString("role"), rs.getBoolean("enabled"),
                        rs.getString("email"), rs.getString("phone"), rs.getString("bio"),
                        rs.getBoolean("must_change_password"), rs.getInt("failed_login_attempts"),
                        rs.getString("avatar_content_type"), rs.getBytes("avatar_content")), id).stream().findFirst();
    }

    public UserAccount save(UserAccount user) {
        if (user.getId() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "insert into user_account(login_name, password_hash, display_name, role, enabled, email, phone, bio, must_change_password, failed_login_attempts, avatar_content_type, avatar_content) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, user.getLoginName());
                statement.setString(2, user.getPasswordHash());
                statement.setString(3, user.getDisplayName());
                statement.setString(4, user.getRole().name());
                statement.setBoolean(5, user.isEnabled());
                statement.setString(6, user.getEmail());
                statement.setString(7, user.getPhone());
                statement.setString(8, user.getBio());
                statement.setBoolean(9, user.isMustChangePassword());
                statement.setInt(10, user.getFailedLoginAttempts());
                statement.setString(11, user.getAvatarContentType());
                statement.setBytes(12, user.getAvatarContent());
                return statement;
            }, keys);
            if (keys.getKey() != null) user.setId(keys.getKey().longValue());
        } else {
            jdbc.update("update user_account set login_name=?, password_hash=?, display_name=?, role=?, enabled=?, email=?, phone=?, bio=?, must_change_password=?, failed_login_attempts=?, avatar_content_type=?, avatar_content=? where id=?",
                    user.getLoginName(), user.getPasswordHash(), user.getDisplayName(), user.getRole().name(), user.isEnabled(),
                    user.getEmail(), user.getPhone(), user.getBio(), user.isMustChangePassword(), user.getFailedLoginAttempts(), user.getAvatarContentType(),
                    user.getAvatarContent(), user.getId());
        }
        return user;
    }

    public void clear() {
        jdbc.update("delete from auth_session");
        jdbc.update("delete from user_account");
    }

    private static UserAccount map(long id, String loginName, String passwordHash, String displayName, String role, boolean enabled,
                                   String email, String phone, String bio, boolean mustChangePassword, int failedLoginAttempts,
                                   String avatarContentType, byte[] avatarContent) {
        UserAccount user = new UserAccount(id, loginName, passwordHash, displayName, Role.valueOf(role), enabled);
        user.setEmail(email);
        user.setPhone(phone);
        user.setBio(bio);
        user.setMustChangePassword(mustChangePassword);
        user.setFailedLoginAttempts(failedLoginAttempts);
        user.setAvatarContentType(avatarContentType);
        user.setAvatarContent(avatarContent);
        return user;
    }
}
