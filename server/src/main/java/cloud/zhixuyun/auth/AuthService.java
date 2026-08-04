package cloud.zhixuyun.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{6,24}$");

    private final UserRepository users;
    private final AuthSessionService sessions;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository users, AuthSessionService sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    public LoginResponse login(LoginRequest request) {
        if (request == null || request.normalizedAccount().isBlank() || request.password() == null || request.password().isBlank()) {
            throw invalidCredentials();
        }
        UserAccount user = users.findByLoginName(request.normalizedAccount()).orElseThrow(AuthService::invalidCredentials);
        if (!user.isEnabled()) {
            throw accountDisabled();
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedLogin(user);
        }
        if (user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            users.save(user);
        }
        return new LoginResponse(sessions.create(user).getToken(), CurrentUserResponse.from(user));
    }

    public CurrentUserResponse me(String authorizationHeader) {
        return CurrentUserResponse.from(sessions.requireUser(authorizationHeader));
    }

    public ProfileResponse profile(String authorizationHeader) {
        return ProfileResponse.from(sessions.requireUser(authorizationHeader));
    }

    public ProfileResponse updateProfile(String authorizationHeader, ProfileResponse request) {
        UserAccount user = sessions.requireUser(authorizationHeader);
        String displayName = trimToNull(request.displayName());
        if (displayName == null || displayName.length() > 80) {
            throw invalidRequest("Display name is required and must be 80 characters or fewer");
        }
        String email = trimToNull(request.email());
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw invalidRequest("Email format is invalid");
        }
        String phone = trimToNull(request.phone());
        if (phone != null && (phone.length() > 24 || !PHONE.matcher(phone).matches())) {
            throw invalidRequest("Phone format is invalid");
        }
        String bio = trimToNull(request.bio());
        if (bio != null && bio.length() > 200) {
            throw invalidRequest("Bio must be 200 characters or fewer");
        }
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setBio(bio);
        users.save(user);
        return ProfileResponse.from(user);
    }

    public void changePassword(String authorizationHeader, ChangePasswordRequest request) {
        UserAccount user = sessions.requireUser(authorizationHeader);
        if (request == null || request.oldPassword() == null || !passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw invalidRequest("Current password is incorrect");
        }
        if (request.newPassword() == null || request.newPassword().length() < 6) {
            throw invalidRequest("New password must be at least 6 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        users.save(user);
        sessions.revokeAll(user.getId());
    }

    public ProfileResponse uploadAvatar(String authorizationHeader, MultipartFile file) {
        UserAccount user = sessions.requireUser(authorizationHeader);
        if (file == null || file.isEmpty()) {
            throw invalidRequest("Avatar file is required");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw invalidRequest("Avatar file must be 2MB or smaller");
        }
        try {
            user.setAvatarContentType(detectImage(file));
            user.setAvatarContent(file.getBytes());
            users.save(user);
            return ProfileResponse.from(user);
        } catch (IOException exception) {
            throw invalidRequest("Avatar file is invalid");
        }
    }

    public AvatarContent avatar(String authorizationHeader) {
        UserAccount user = sessions.requireUser(authorizationHeader);
        if (user.getAvatarContent() == null || user.getAvatarContentType() == null) {
            throw new AuthException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Avatar not found");
        }
        return new AvatarContent(user.getAvatarContentType(), user.getAvatarContent());
    }

    public ProfileResponse deleteAvatar(String authorizationHeader) {
        UserAccount user = sessions.requireUser(authorizationHeader);
        user.setAvatarContent(null);
        user.setAvatarContentType(null);
        users.save(user);
        return ProfileResponse.from(user);
    }

    public void logout(String authorizationHeader) {
        sessions.requireUser(authorizationHeader);
        sessions.revoke(authorizationHeader);
    }

    public String encodePassword(String password) {
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Password must not be blank");
        return passwordEncoder.encode(password);
    }

    private void recordFailedLogin(UserAccount user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts > MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setEnabled(false);
        }
        users.save(user);
        if (!user.isEnabled()) {
            sessions.revokeAll(user.getId());
            throw accountDisabled();
        }
        throw invalidCredentials();
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String detectImage(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            if (header.length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) return "image/jpeg";
            if (header.length >= 8 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) return "image/png";
            if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') return "image/webp";
        }
        throw invalidRequest("Only JPG, PNG, and WebP avatars are supported");
    }

    private static AuthException invalidRequest(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid account or password");
    }

    private static AuthException accountDisabled() {
        return new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Account has been disabled");
    }

    public record AvatarContent(String contentType, byte[] content) {
    }
}
