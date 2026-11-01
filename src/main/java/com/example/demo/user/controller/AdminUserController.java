package com.example.demo.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.shared.web.ApiResponse;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.web.PageQuery;
import com.example.demo.user.api.dto.UserStatusUpdateRequest;
import com.example.demo.user.api.dto.UserProfileResponse;
import com.example.demo.user.api.UserRole;
import com.example.demo.user.service.AdminUserService;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;

@Tag(name = "Admin - Users", description = "Quản lý người dùng; yêu cầu quyền ADMIN.")
@RestController
@RequestMapping({"/api/admin/users", "/api/users"})
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "Danh sách người dùng và bộ lọc")
    public ApiResponse<PageResponse<UserProfileResponse>> getAllUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) UserRole role,
            @Valid @ModelAttribute PageQuery pagination) {
        return new ApiResponse<>(200, "Thành công",
                adminUserService.getUsers(keyword, isActive, role, pagination));
    }

    /** Khóa / mở khóa tài khoản. Tài khoản ADMIN không thể bị khóa. */
    @PutMapping("/{id}/status")
    @Operation(summary = "Khóa hoặc mở khóa tài khoản")
    public ApiResponse<UserProfileResponse> updateUserStatus(@PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return new ApiResponse<>(200, "Cập nhật trạng thái tài khoản thành công",
                adminUserService.updateUserStatus(id, request.isActive()));
    }
}
