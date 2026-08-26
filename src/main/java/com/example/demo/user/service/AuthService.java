package com.example.demo.user.service;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.demo.user.dto_request.UserCreationRequest;
import com.example.demo.user.entity.InvalidatedToken;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.InvalidatedTokenRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.sercurity.JwtUtils;

import jakarta.transaction.Transactional;
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    public AuthService(UserRepository userRepository, JwtUtils jwtUtils, RefreshTokenService refreshTokenService, InvalidatedTokenRepository invalidatedTokenRepository) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.invalidatedTokenRepository = invalidatedTokenRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }
    public User createUser(UserCreationRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // Phải mã hóa
        user.setRole(UserRole.CUSTOMER); // Gán vai trò mặc định là USER
        return userRepository.save(user);
    }
    public List<ResponseCookie> login(UserCreationRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_INVALID));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.AUTH_INVALID); 
        }
        if (!user.isActive()) {
        throw new AppException(ErrorCode.USER_LOCKED);
    }

        String token = jwtUtils.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId()).getToken();
        List<ResponseCookie> cookies = new ArrayList<>();
        ResponseCookie jwtCookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(15 * 60) // 15 phút
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_jwt", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 ngày
                .build();
        cookies.add(jwtCookie);
        cookies.add(refreshCookie);
        return cookies;
    }
    @Transactional
    public void logout(String token) {
    String jwtId = jwtUtils.getJwtIdFromJwtToken(token);
    Date expiryTime = jwtUtils.getExpirationDateFromJwtToken(token);
    String userIdStr = jwtUtils.getUserIdFromJwtToken(token);
    // 2. Lưu vào bảng Blacklist
    InvalidatedToken invalidatedToken = new InvalidatedToken(jwtId, expiryTime);
    invalidatedTokenRepository.save(invalidatedToken);
    if (userIdStr != null) {
        Long userId = Long.parseLong(userIdStr);
        refreshTokenService.deleteByUserId(userId);
    }
    }
    public boolean checkPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
    public String generateToken(User user) {
        return jwtUtils.generateToken(user);
    }   
    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null); 
    }
}
