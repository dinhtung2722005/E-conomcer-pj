package com.example.demo.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload admin cập nhật tài khoản người dùng.
 *
 * <p>Cố ý <b>không</b> có trường mật khẩu: mật khẩu chỉ được đổi bởi chính chủ
 * tài khoản qua {@code PUT /api/details/password}, nhờ vậy tránh việc admin
 * (hoặc kẻ tấn công chiếm được phiên admin) đặt lại mật khẩu của người khác.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserUpdateRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;
}
