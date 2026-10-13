package com.example.demo.user.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.LoginRequest;
import com.example.demo.user.dto_request.LoginResponse;
import com.example.demo.user.dto_request.UserCreationRequest;
import com.example.demo.user.dto_request.UserProfileResponse;
import com.example.demo.user.entity.RefreshToken;
import com.example.demo.user.entity.User;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.security.JwtUtils;
import com.example.demo.user.service.AuthService;
import com.example.demo.user.service.RefreshTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@Valid @RequestBody UserCreationRequest request) {
        UserProfileResponse user = authService.register(request);
        return ResponseEntity.status(201)
                .body(new ApiResponse<>(201, "Tài khoản đã được tạo thành công", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);

        String accessToken = authService.generateAccessToken(user);
        RefreshToken refreshToken = authService.createRefreshToken(user.getId());

        LoginResponse data = new LoginResponse(AuthService.toProfile(user), accessToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.buildAccessCookie(accessToken).toString())
                .header(HttpHeaders.SET_COOKIE, authService.buildRefreshCookie(refreshToken.getToken()).toString())
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
        User user = refreshTokenService.consumeRefreshToken(refreshTokenValue);

        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_LOCKED);
        }

        String newAccessToken = authService.generateAccessToken(user);
        RefreshToken newRefreshToken = authService.createRefreshToken(user.getId());

        LoginResponse data = new LoginResponse(AuthService.toProfile(user), newAccessToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.buildAccessCookie(newAccessToken).toString())
                .header(HttpHeaders.SET_COOKIE, authService.buildRefreshCookie(newRefreshToken.getToken()).toString())
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
