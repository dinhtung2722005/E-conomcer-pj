package com.example.demo.user.controller;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.LoginResponse;
import com.example.demo.user.dto_request.UserCreationRequest;
import com.example.demo.user.entity.RefreshToken;
import com.example.demo.user.entity.User;
import com.example.demo.user.sercurity.JwtUtils;
import com.example.demo.user.service.AuthService;
import com.example.demo.user.service.RefreshTokenService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;
    public AuthController(AuthService authService, RefreshTokenService refreshTokenService, JwtUtils jwtUtils) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtils = jwtUtils;
    }
    @PostMapping("/refresh")
    public ApiResponse<?> refreshToken(HttpServletRequest request, HttpServletResponse servletResponse) {
    String refreshToken = null;
    boolean isFromCookie = false; 
    
    String headerToken = request.getHeader("Refresh-Token");
    if (headerToken != null && !headerToken.isEmpty()) {
        refreshToken = headerToken; 
    } else if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
            if ("refresh_jwt".equals(cookie.getName())) {
                refreshToken = cookie.getValue();
                isFromCookie = true;
                break;
            }
        }
    }
    
    if (refreshToken == null) {
        return new ApiResponse<>(400, "Không tìm thấy Refresh Token", null);
    }
    
    final boolean finalIsFromCookie = isFromCookie;
    
    try {
        return refreshTokenService.findByToken(refreshToken)
            .map(refreshTokenService::verifyExpiration)
            .map(RefreshToken::getUser)
            .map(user -> {
                
                String newAccessToken = jwtUtils.generateToken(user);
                
                
                if (finalIsFromCookie) { 
                    ResponseCookie jwtCookie = ResponseCookie.from("jwt", newAccessToken)
                        .httpOnly(true).secure(true).path("/").maxAge(15 * 60).build(); 
                    servletResponse.setHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
                    return new ApiResponse<>(200, "Refresh Token thành công", null);
                } else {
                    return new ApiResponse<>(200, "Refresh Token thành công", newAccessToken);
                }
            })
            .orElseThrow(() -> new RuntimeException("Refresh token không tồn tại trong DB!"));
    } catch (Exception e) {
        return new ApiResponse<>(401, "Refresh Token không hợp lệ hoặc đã hết hạn", null);
    }
}
    @PostMapping("/register")
    public ApiResponse<User> registerUser(@RequestBody UserCreationRequest request) {
        User user = authService.createUser(request);
        ApiResponse<User> response = new ApiResponse<>();
        response.setCode(201);
        response.setMessage("Tài khoản đã được tạo thành công");
        response.setResult(user);
        return response;
    }
    @PostMapping("/login")
    public ApiResponse<LoginResponse> loginUser(@RequestBody UserCreationRequest request,HttpServletResponse servletResponse) {
        List<ResponseCookie> cookies = authService.login(request);
        User user = authService.findByUsername(request.getUsername()); 
        String token = cookies.get(0).getValue();
        String refreshToken = cookies.get(1).getValue();
        LoginResponse loginData = new LoginResponse(user, token);
        ApiResponse<LoginResponse> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Đăng nhập thành công");
        response.setResult(loginData);
        for (ResponseCookie cookie : cookies) {
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
        return response;
    }
    @PostMapping("/logout")
public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse servletResponse) {
    String token = null;
    

    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        token = authHeader.substring(7);
    } else if (request.getCookies() != null) {
        for (Cookie cookie : request.getCookies()) {
            if ("jwt".equals(cookie.getName())) {
                token = cookie.getValue();
                break;
            }
        }
    }

  
    if (token != null && !token.isEmpty()) {
        authService.logout(token); 
    }
    ResponseCookie clearJwtCookie = ResponseCookie.from("jwt", "")
            .httpOnly(true).secure(false) // Đổi thành true nếu chạy HTTPS
            .path("/").maxAge(0).build();
            
    ResponseCookie clearRefreshCookie = ResponseCookie.from("refresh_jwt", "")
            .httpOnly(true).secure(false) // Đổi thành true nếu chạy HTTPS
            .path("/").maxAge(0).build();
    servletResponse.addHeader(HttpHeaders.SET_COOKIE, clearJwtCookie.toString());
    servletResponse.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString());
    ApiResponse<Void> apiResponse = new ApiResponse<>();
    apiResponse.setCode(200);
    apiResponse.setMessage("Đăng xuất thành công");
    
    return apiResponse;
}
    
}
