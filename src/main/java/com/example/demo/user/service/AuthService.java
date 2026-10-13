package com.example.demo.user.service;

import java.util.Date;

import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.dto_request.LoginRequest;
import com.example.demo.user.dto_request.UserCreationRequest;
import com.example.demo.user.dto_request.UserProfileResponse;
import com.example.demo.user.entity.InvalidatedToken;
import com.example.demo.user.entity.RefreshToken;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.InvalidatedTokenRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.JwtUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** SameSite=Strict: chặn trình duyệt gửi cookie trong request cross-site (chống CSRF). */
    private static final String SAME_SITE = "Strict";

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserProfileResponse register(UserCreationRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                .build();

        User saved = userRepository.save(user);
        return toProfile(saved);
    }

    /**
     * Xác thực thông tin đăng nhập.
     *
     * <p>Thông báo lỗi luôn giống nhau dù sai username hay sai mật khẩu, để không
     * cho phép kẻ tấn công dò tên tài khoản tồn tại (user enumeration).
     */
    @Transactional
    public User login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_INVALID));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.AUTH_INVALID);
        }
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_LOCKED);
        }
        return user;
    }

    public String generateAccessToken(User user) {
        return jwtUtils.generateToken(user);
    }

    public ResponseCookie buildAccessCookie(String token) {
        return ResponseCookie.from(JwtUtils.ACCESS_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(jwtUtils.getAccessExpirationMs() / 1000)
                .build();
    }

    public ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from(JwtUtils.REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(jwtUtils.getRefreshExpirationMs() / 1000)
                .build();
    }

    public ResponseCookie clearAccessCookie() {
        return clearCookie(JwtUtils.ACCESS_COOKIE);
    }

    public ResponseCookie clearRefreshCookie() {
        return clearCookie(JwtUtils.REFRESH_COOKIE);
    }

    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(0)
                .build();
    }

    /**
     * Vô hiệu hóa access token hiện tại (đưa {@code jti} vào blacklist) và thu hồi
     * refresh token của người dùng. Nhờ vậy logout có hiệu lực ngay lập tức.
     */
    @Transactional
    public void logout(String token) {
        if (token == null || token.isEmpty() || !jwtUtils.validateJwtToken(token)) {
            return;
        }

        String jwtId = jwtUtils.getJwtIdFromJwtToken(token);
        Date expiryTime = jwtUtils.getExpirationDateFromJwtToken(token);
        String userIdStr = jwtUtils.getUserIdFromJwtToken(token);

        if (jwtId != null && !invalidatedTokenRepository.existsById(jwtId)) {
            invalidatedTokenRepository.save(new InvalidatedToken(jwtId, expiryTime));
        }

        if (userIdStr != null) {
            refreshTokenService.revokeByUserId(Long.parseLong(userIdStr));
        }
    }

    /** Thu hồi toàn bộ phiên của một người dùng (dùng khi đổi mật khẩu / khóa tài khoản). */
    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenService.revokeByUserId(userId);
    }

    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        return refreshTokenService.createRefreshToken(userId);
    }

    public static UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .avatar(user.getAvatar())
                .build();
    }
}
