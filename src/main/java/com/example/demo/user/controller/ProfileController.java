package com.example.demo.user.controller;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpHeaders;
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
import com.example.demo.user.security.JwtUtils;
import com.example.demo.user.service.AuthService;
import com.example.demo.user.service.CloudinaryService;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/details")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    private final UserService userService;
    private final AuthService authService;
    private final CloudinaryService cloudinaryService;
    private final CurrentUserService currentUserService;
    private final JwtUtils jwtUtils;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile() {
        UserProfileResponse profile = userService.profile(currentUserService.getCurrentUsername());
        return new ApiResponse<>(200, "Lấy thông tin profile thành công", profile);
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequest profileRequest) {
        UserProfileResponse updated = userService.updateProfile(currentUserService.getCurrentUsername(), profileRequest);
        return new ApiResponse<>(200, "Cập nhật thông tin profile thành công", updated);
    }

    /** Đổi mật khẩu, sau đó thu hồi mọi phiên và bắt đăng nhập lại. */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        String username = currentUserService.getCurrentUsername();
        Long userId = currentUserService.getCurrentUserId();

        userService.changePassword(username, request);

        // Đưa access token hiện tại vào blacklist và thu hồi refresh token.
        authService.logout(jwtUtils.getJwtFromRequest(httpRequest));
        authService.revokeAllSessions(userId);

        return new ApiResponse<>(200, "Đổi mật khẩu thành công. Vui lòng đăng nhập lại!", null);
    }

    @PostMapping("/avatar")
    public ApiResponse<UserProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        validateImage(file);

        try {
            String avatarUrl = cloudinaryService.uploadImage(file);
            UserProfileResponse updated = userService.updateAvatar(currentUserService.getCurrentUsername(), avatarUrl);
            return new ApiResponse<>(200, "Cập nhật ảnh đại diện thành công", updated);
        } catch (IOException e) {
            log.error("Upload avatar lên Cloudinary thất bại", e);
            throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED);
        }
    }

    /** Chỉ nhận ảnh thật: kiểm tra content-type do client khai báo, phần mở rộng và kích thước. */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.AVATAR_INVALID, "Vui lòng chọn một file ảnh");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new AppException(ErrorCode.AVATAR_INVALID, "Ảnh không được vượt quá 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.AVATAR_INVALID);
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            String lower = originalName.toLowerCase();
            boolean allowedExtension = lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif");
            if (!allowedExtension) {
                throw new AppException(ErrorCode.AVATAR_INVALID);
            }
        }
    }
}
