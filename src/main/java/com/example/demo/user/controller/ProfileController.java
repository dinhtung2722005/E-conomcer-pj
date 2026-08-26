package com.example.demo.user.controller;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.ChangePasswordRequest;
import com.example.demo.user.dto_request.UserProfileResponse;
import com.example.demo.user.dto_request.UserProfileUpdateRequest;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.service.AuthService;
import com.example.demo.user.service.CloudinaryService;
import com.example.demo.user.service.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
@RestController
@RequestMapping("/api/details")
public class ProfileController {

    @Autowired
    private UserService userService;
    @Autowired
    private AuthService authService;
    @Autowired
    private CloudinaryService cloudinaryService;
    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        UserProfileResponse profile = userService.profile(username);
        
        ApiResponse<UserProfileResponse> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Lấy thông tin profile thành công");
        response.setResult(profile);
        
        return response;
    }
    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(@RequestBody UserProfileUpdateRequest profileRequest) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        
        UserProfileResponse updatedProfile = userService.updateProfile(username, profileRequest);
        
        ApiResponse<UserProfileResponse> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Cập nhật thông tin profile thành công");
        response.setResult(updatedProfile);
        
        return response;
    }
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        // 1. Lấy username từ Token
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. Gọi Service để đổi mật khẩu
        userService.changePassword(username, request);

        // 3. Lấy token hiện tại để đưa vào Blacklist (Giống logic hàm Logout)
        String token = null;
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (httpRequest.getCookies() != null) {
            for (Cookie cookie : httpRequest.getCookies()) {
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
                .httpOnly(true).secure(true).path("/").maxAge(0).build();
        ResponseCookie clearRefreshCookie = ResponseCookie.from("refresh_jwt", "")
                .httpOnly(true).secure(true).path("/").maxAge(0).build();

        httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearJwtCookie.toString());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString());

        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("Đổi mật khẩu thành công. Vui lòng đăng nhập lại!");
        return response;
    }
    @PostMapping("/avatar")
    public ApiResponse<UserProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        
        if (file == null || file.isEmpty()) {
            return new ApiResponse<>(400, "Vui lòng chọn một file ảnh!", null);
        }

        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();

            String avatarUrl = cloudinaryService.uploadImage(file);

            UserProfileResponse updatedProfile = userService.updateAvatar(username, avatarUrl);
            
            return new ApiResponse<>(200, "Cập nhật ảnh đại diện thành công", updatedProfile);

        } catch (IOException e) {
            throw new AppException(ErrorCode.Imgaes_Ivalid);
        }
    }   
    

}