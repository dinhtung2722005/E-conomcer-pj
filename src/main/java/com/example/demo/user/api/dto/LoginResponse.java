package com.example.demo.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kết quả đăng nhập trả về cho client.
 *
 * <p>Chỉ chứa DTO hồ sơ, tuyệt đối không trả entity {@code User}
 * (sẽ làm lộ hash mật khẩu và các trường nội bộ).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private UserProfileResponse user;
    private String accessToken;
}
