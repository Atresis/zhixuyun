package cloud.zhixuyun.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        auth.logout(authorization);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return auth.me(authorization);
    }

    @GetMapping("/profile")
    public ProfileResponse profile(@RequestHeader("Authorization") String authorization) {
        return auth.profile(authorization);
    }

    @PatchMapping("/profile")
    public ProfileResponse updateProfile(@RequestHeader("Authorization") String authorization, @RequestBody ProfileResponse request) {
        return auth.updateProfile(authorization, request);
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse uploadAvatar(@RequestHeader("Authorization") String authorization, @RequestPart("file") MultipartFile file) {
        return auth.uploadAvatar(authorization, file);
    }

    @GetMapping("/profile/avatar")
    public ResponseEntity<byte[]> avatar(@RequestHeader("Authorization") String authorization) {
        AuthService.AvatarContent avatar = auth.avatar(authorization);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.content());
    }

    @DeleteMapping("/profile/avatar")
    public ProfileResponse deleteAvatar(@RequestHeader("Authorization") String authorization) {
        return auth.deleteAvatar(authorization);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestHeader("Authorization") String authorization, @RequestBody ChangePasswordRequest request) {
        auth.changePassword(authorization, request);
        return ResponseEntity.noContent().build();
    }
}
