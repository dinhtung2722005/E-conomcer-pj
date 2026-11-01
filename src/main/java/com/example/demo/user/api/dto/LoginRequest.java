package com.example.demo.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload đăng nhập. Không ràng buộc độ dài mật khẩu để tài khoản cũ
 * (tạo trước khi áp chính sách mật khẩu mới) vẫn đăng nhập được.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Schema(description = "Tên đăng nhập của tài khoản", example = "demo_customer")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Schema(description = "Mật khẩu đăng nhập", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;
}
