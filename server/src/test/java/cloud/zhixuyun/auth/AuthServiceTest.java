package cloud.zhixuyun.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "zhixuyun.demo-data=false",
        "DATABASE_URL=jdbc:h2:mem:auth-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "BACKUP_DATABASE_URL=jdbc:h2:mem:auth-service-backup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class AuthServiceTest {
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthService auth;

    @BeforeEach
    void setUp() {
        sessions.clear();
        users.clear();
        users.save(new UserAccount(null, "teacher@example.com", auth.encodePassword("secret123"), "Teacher", Role.TEACHER, true));
    }

    @Test
    void loginReturnsTokenAndServerOwnedRole() {
        LoginResponse result = auth.login(new LoginRequest("teacher@example.com", "secret123"));
        assertFalse(result.token().isBlank());
        assertEquals(Role.TEACHER, result.user().role());
        assertEquals("teacher@example.com", result.user().loginName());
    }

    @Test
    void invalidCredentialsReturn401Contract() {
        AuthException exception = assertThrows(AuthException.class,
                () -> auth.login(new LoginRequest("teacher@example.com", "wrong")));
        assertEquals(401, exception.getStatus().value());
        assertEquals("INVALID_CREDENTIALS", exception.getCode());
    }

    @Test
    void disabledAccountCannotLogin() {
        UserAccount user = users.findByLoginName("teacher@example.com").orElseThrow();
        user.setEnabled(false);
        users.save(user);
        AuthException exception = assertThrows(AuthException.class,
                () -> auth.login(new LoginRequest("teacher@example.com", "secret123")));
        assertEquals(403, exception.getStatus().value());
        assertEquals("ACCOUNT_DISABLED", exception.getCode());
    }

    @Test
    void profileCanBeUpdatedAndValidated() {
        String token = auth.login(new LoginRequest("teacher@example.com", "secret123")).token();
        ProfileResponse profile = auth.updateProfile("Bearer " + token,
                new ProfileResponse(null, null, "Teacher Zhang", "teacher@example.com", "13800138000", "Bio", null, false, false, false));
        assertEquals("Teacher Zhang", profile.displayName());
        assertEquals("teacher@example.com", profile.email());
        assertEquals("13800138000", profile.phone());
        assertEquals("Bio", profile.bio());
    }

    @Test
    void changePasswordRevokesExistingSession() {
        String token = auth.login(new LoginRequest("teacher@example.com", "secret123")).token();
        auth.changePassword("Bearer " + token, new ChangePasswordRequest("secret123", "newsecret"));
        AuthException expired = assertThrows(AuthException.class, () -> auth.me("Bearer " + token));
        assertEquals("SESSION_EXPIRED", expired.getCode());
        String newToken = auth.login(new LoginRequest("teacher@example.com", "newsecret")).token();
        assertFalse(newToken.isBlank());
    }

    @Test
    void avatarCanBeUploadedAndDeleted() {
        String token = auth.login(new LoginRequest("teacher@example.com", "secret123")).token();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4});
        ProfileResponse profile = auth.uploadAvatar("Bearer " + token, file);
        assertEquals(true, profile.hasAvatar());
        AuthService.AvatarContent avatar = auth.avatar("Bearer " + token);
        assertEquals("image/png", avatar.contentType());
        ProfileResponse cleared = auth.deleteAvatar("Bearer " + token);
        assertFalse(cleared.hasAvatar());
        assertNull(users.findByLoginName("teacher@example.com").orElseThrow().getAvatarContent());
    }

    @Test
    void accountIsDisabledAfterMoreThanFiveFailedLogins() {
        for (int i = 0; i < 5; i++) {
            final String password = "wrong-" + i;
            AuthException exception = assertThrows(AuthException.class,
                    () -> auth.login(new LoginRequest("teacher@example.com", password)));
            assertEquals(401, exception.getStatus().value());
            assertEquals("INVALID_CREDENTIALS", exception.getCode());
        }

        AuthException locked = assertThrows(AuthException.class,
                () -> auth.login(new LoginRequest("teacher@example.com", "wrong-final")));
        assertEquals(403, locked.getStatus().value());
        assertEquals("ACCOUNT_DISABLED", locked.getCode());
        assertFalse(users.findByLoginName("teacher@example.com").orElseThrow().isEnabled());
    }

    @Test
    void successfulLoginResetsFailureCounter() {
        assertThrows(AuthException.class, () -> auth.login(new LoginRequest("teacher@example.com", "wrong")));
        assertEquals(1, users.findByLoginName("teacher@example.com").orElseThrow().getFailedLoginAttempts());

        auth.login(new LoginRequest("teacher@example.com", "secret123"));
        assertEquals(0, users.findByLoginName("teacher@example.com").orElseThrow().getFailedLoginAttempts());
    }
}
