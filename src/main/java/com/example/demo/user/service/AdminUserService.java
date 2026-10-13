package com.example.demo.user.service;

import java.util.stream.Collectors;

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
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getUsers(String keyword, Boolean isActive, String role, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by("id").descending());
        Specification<User> spec = UserSpecification.filterUsers(keyword, isActive, role);
        Page<User> userPage = userRepository.findAll(spec, pageable);

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
                .currentPage(safePage)
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .data(dtoList)
                .build();
    }

    /** Khóa / mở khóa tài khoản. Không cho phép khóa tài khoản ADMIN. */
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == UserRole.ADMIN) {
            throw new AppException(ErrorCode.ADMIN_CANNOT_BE_LOCKED);
        }

        user.setActive(!user.isActive());
        userRepository.save(user);
    }
}
