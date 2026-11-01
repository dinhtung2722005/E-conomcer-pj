package com.example.demo.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.shared.web.ApiResponse;
import com.example.demo.user.api.dto.UserCreationRequest;
import com.example.demo.user.api.dto.UserProfileResponse;
import com.example.demo.user.api.dto.UserUpdateRequest;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Quản trị người dùng. Toàn bộ endpoint yêu cầu quyền ADMIN
 * (đã chặn ở tầng URL trong {@code SecurityConfig} và ở đây bằng {@code @PreAuthorize}).
 */
@Slf4j
@RestController
@RequestMapping({"/api/admin/users", "/api/users"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final com.example.demo.user.api.RegistrationUseCase registration;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserProfileResponse> createUser(@Valid @RequestBody UserCreationRequest request) {
        return new ApiResponse<>(201, "Tạo tài khoản thành công", registration.createUser(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserProfileResponse> getUserById(@PathVariable Long id) {
        return new ApiResponse<>(200, "Thành công", userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserProfileResponse> updateUser(@PathVariable Long id,
                                                      @Valid @RequestBody UserUpdateRequest request) {
        return new ApiResponse<>(200, "Cập nhật tài khoản thành công", userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        UserPrincipal current = (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        userService.deleteUser(id, current.getId());
        return new ApiResponse<>(200, "Xóa tài khoản thành công", null);
    }
}
