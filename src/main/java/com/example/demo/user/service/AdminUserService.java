package com.example.demo.user.service;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.user.dto_request.UserProfileResponse;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;

@Service
public class AdminUserService {

    @Autowired
    private UserRepository userRepository;

    // 1. Lấy danh sách có phân trang và tìm kiếm
        public PageResponse<UserProfileResponse> getUsers(String keyword, Boolean isActive, String role, int page, int size) {
            // Sắp xếp ID giảm dần (User mới nhất lên đầu)
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("id").descending()); 

        Specification<User> spec = UserSpecification.filterUsers(keyword, isActive, role);
        Page<User> userPage = userRepository.findAll(spec, pageable);

        // Map Entity sang DTO
        var dtoList = userPage.getContent().stream()
        .map(user -> UserProfileResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .role(user.getRole().name()) 
        .avatar(user.getAvatar())
        .build())
    .collect(Collectors.toList());

        return PageResponse.<UserProfileResponse>builder()
                .currentPage(page)
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .data(dtoList)
                .build();
    }

    // 2. Khóa / Mở khóa tài khoản
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (user.getRole().name().equals("ADMIN")) {
            throw new RuntimeException("Không thể khóa tài khoản Admin khác!");
        }

        user.setActive(!user.isActive());
        userRepository.save(user);
    }
}