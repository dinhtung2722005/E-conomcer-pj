package com.example.demo.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto_request.ApiResponse;
import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.user.dto_request.UserProfileResponse;
import com.example.demo.user.service.AdminUserService;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')") 
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

   @GetMapping
    public ApiResponse<PageResponse<UserProfileResponse>> getAllUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isActive, // Tham số mới
            @RequestParam(required = false) String role,      // Tham số mới
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return new ApiResponse<>(200, "Thành công", 
                adminUserService.getUsers(keyword, isActive, role, page, size));
    }

    // API Khóa/Mở khóa tài khoản
    @PutMapping("/{id}/toggle-status")
    public ApiResponse<Void> toggleUserStatus(@PathVariable Long id) {
        adminUserService.toggleUserStatus(id);
        return new ApiResponse<>(200, "Đã thay đổi trạng thái tài khoản thành công", null);
    }
}