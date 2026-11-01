package com.example.demo.user.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.shared.web.ApiResponse;
import com.example.demo.user.api.dto.LoginRequest;
import com.example.demo.user.api.dto.LoginResponse;
import com.example.demo.user.api.dto.UserCreationRequest;
import com.example.demo.user.api.dto.UserProfileResponse;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.security.JwtUtils;
import com.example.demo.user.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.example.demo.user.api.RegistrationUseCase registration;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@Valid @RequestBody UserCreationRequest request) {
        UserProfileResponse user = registration.register(request);
        return ResponseEntity.status(201)
                .body(new ApiResponse<>(201, "Tài khoản đã được tạo thành công", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.Session session = authService.login(request);
        LoginResponse data = new LoginResponse(session.user(), session.accessToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.buildAccessCookie(session.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authService.buildRefreshCookie(session.refreshToken()).toString())
                .body(new ApiResponse<>(200, "Đăng nhập thành công", data));
    }

    /**
     * Cấp lại access token từ refresh token.
     *
     * <p>Refresh token cũ bị thu hồi và thay bằng token mới (rotation) nên không thể
     * phát lại (replay) nhiều lần.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(HttpServletRequest request) {
        String refreshTokenValue = jwtUtils.getRefreshTokenFromRequest(request);
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        // Ném AppException(401) nếu token không tồn tại hoặc đã hết hạn.
        AuthService.Session session = authService.refresh(refreshTokenValue);
        LoginResponse data = new LoginResponse(session.user(), session.accessToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.buildAccessCookie(session.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authService.buildRefreshCookie(session.refreshToken()).toString())
                .body(new ApiResponse<>(200, "Refresh token thành công", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(jwtUtils.getJwtFromRequest(request));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.clearAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString())
                .body(new ApiResponse<>(200, "Đăng xuất thành công", null));
    }
}
