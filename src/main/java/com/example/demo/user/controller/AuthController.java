package com.example.demo.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(name = "Authentication", description = "Đăng ký, đăng nhập và quản lý phiên JWT.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.example.demo.user.api.RegistrationUseCase registration;
    private final JwtUtils jwtUtils;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản", description = "Tạo tài khoản và khởi tạo ví trong cùng transaction. HTTP 201 khi thành công.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Đã tạo tài khoản", useReturnTypeSchema = true)
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(@Valid @RequestBody UserCreationRequest request) {
        UserProfileResponse user = registration.register(request);
        return ResponseEntity.status(201)
                .body(new ApiResponse<>(201, "Tài khoản đã được tạo thành công", user));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Trả access token tại result.accessToken và thiết lập cookie HttpOnly jwt, refresh_jwt.")
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
    @Operation(summary = "Làm mới phiên đăng nhập", description = "Gửi cookie refresh_jwt hoặc header Refresh-Token. Refresh token dùng một lần và được thay sau mỗi lần gọi.")
    @Parameter(name = "Refresh-Token", in = ParameterIn.HEADER, required = false,
            description = "Refresh token nếu không dùng cookie refresh_jwt; chỉ dùng một lần. Header được ưu tiên hơn cookie.")
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
    @Operation(summary = "Đăng xuất", description = "Thu hồi access token nếu còn hợp lệ và xóa cookie phiên. Có thể gọi khi chưa đăng nhập.")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(jwtUtils.getJwtFromRequest(request));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.clearAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString())
                .body(new ApiResponse<>(200, "Đăng xuất thành công", null));
    }
}
