package com.example.demo.user.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.dto_request.PageResponse;
import com.example.demo.user.dto_request.PageQuery;
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

    private final UserRepository userRepository;
    private final SessionRevocationService sessionRevocationService;

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> getUsers(String keyword, Boolean isActive, UserRole role, PageQuery pagination) {
        Specification<User> spec = UserSpecification.filterUsers(keyword, isActive, role);
        return PageResponse.from(userRepository.findAll(spec, pagination.toPageable())
                .map(AuthService::toProfile));
    }

    /** Đặt trạng thái cụ thể; gửi lại cùng trạng thái không thu hồi phiên thêm lần nữa. */
    @Transactional
    public UserProfileResponse updateUserStatus(Long userId, boolean active) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() == UserRole.ADMIN && !active) {
            throw new AppException(ErrorCode.ADMIN_CANNOT_BE_LOCKED);
        }

        if (user.isActive() == active) {
            return AuthService.toProfile(user);
        }
        user.setActive(active);
        userRepository.save(user);
        if (!user.isActive()) {
            sessionRevocationService.revokeAllSessions(userId);
        }
        return AuthService.toProfile(user);
    }
}
