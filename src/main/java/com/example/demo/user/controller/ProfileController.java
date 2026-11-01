package com.example.demo.user.controller;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.shared.web.ApiResponse;
import com.example.demo.user.api.dto.ChangePasswordRequest;
import com.example.demo.user.api.dto.UserProfileResponse;
import com.example.demo.user.api.dto.UserProfileUpdateRequest;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.security.JwtUtils;
import com.example.demo.user.service.AuthService;
import com.example.demo.user.service.CloudinaryService;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/details")
@Slf4j
public class ProfileController {

    /** Chỉ nhận các định dạng ảnh phổ biến; nội dung file còn được kiểm tra thêm ở {@link #readValidatedImage}. */
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    @Autowired
    private  UserService userService;
    @Autowired
    private  AuthService authService;
    @Autowired
    private  CloudinaryService cloudinaryService;
    @Autowired
    private  CurrentUserService currentUserService;
    @Autowired
    private  JwtUtils jwtUtils;

    /** Lấy từ cùng một cấu hình với Spring multipart để không bị lệch giới hạn. */
    @Value("${spring.servlet.multipart.max-file-size:5MB}")
    private DataSize maxAvatarSize;

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

    /**
     * Đổi mật khẩu rồi kết thúc toàn bộ phiên đăng nhập.
     *
     * <p>Ba việc phải làm, không được bỏ sót việc nào:
     * <ol>
     *   <li>đưa access token hiện tại vào blacklist;</li>
     *   <li>UserService đổi mật khẩu và thu hồi toàn bộ phiên trong cùng transaction;</li>
     *   <li>xóa cookie phiên trên trình duyệt, nếu không client vẫn gửi kèm cookie
     *       đã chết ở mọi request tiếp theo.</li>
     * </ol>
     */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                            HttpServletRequest httpRequest) {
        String username = currentUserService.getCurrentUsername();

        userService.changePassword(username, request);

        authService.logout(jwtUtils.getJwtFromRequest(httpRequest));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authService.clearAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString())
                .body(new ApiResponse<>(200, "Đổi mật khẩu thành công. Vui lòng đăng nhập lại!", null));
    }

    @PostMapping("/avatar")
    public ApiResponse<UserProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        byte[] content = readValidatedImage(file);

        try {
            String avatarUrl = cloudinaryService.uploadImage(content);
            UserProfileResponse updated = userService.updateAvatar(currentUserService.getCurrentUsername(), avatarUrl);
            return new ApiResponse<>(200, "Cập nhật ảnh đại diện thành công", updated);
        } catch (IOException e) {
            // log.error("Upload avatar lên Cloudinary thất bại", e);
            throw new AppException(ErrorCode.AVATAR_UPLOAD_FAILED);
        }
    }

    /**
     * Đọc và kiểm tra file ảnh, trả về nội dung để upload.
     *
     * <p>Kiểm tra theo 3 lớp:
     * <ul>
     *   <li>kích thước — chặn file quá lớn;</li>
     *   <li>content-type do client khai báo — chặn sớm các trường hợp rõ ràng sai;</li>
     *   <li><b>chữ ký nhị phân</b> của file — đây mới là bằng chứng thật, vì content-type
     *       và phần mở rộng đều do client tự khai và có thể giả mạo.</li>
     * </ul>
     */
    private byte[] readValidatedImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.AVATAR_INVALID, "Vui lòng chọn một file ảnh");
        }
        if (file.getSize() > maxAvatarSize.toBytes()) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE,
                    "Ảnh không được vượt quá " + maxAvatarSize.toMegabytes() + "MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.AVATAR_INVALID);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.AVATAR_INVALID, "Không đọc được file tải lên");
        }

        if (!hasKnownImageSignature(content)) {
            throw new AppException(ErrorCode.AVATAR_INVALID, "Nội dung file không phải là ảnh hợp lệ");
        }

        return content;
    }

    /**
     * Nhận diện ảnh bằng các byte mở đầu (magic number) của JPEG / PNG / GIF / WEBP.
     * Đây là thứ duy nhất trong request mà client không thể tùy ý khai man.
     */
    private static boolean hasKnownImageSignature(byte[] b) {
        if (b.length < 12) {
            return false;
        }
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return true;
        }
        // GIF: "GIF87a" hoặc "GIF89a"
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return true;
        }
        // WEBP: "RIFF" ____ "WEBP"
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
