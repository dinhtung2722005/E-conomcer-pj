package com.example.demo.user.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.demo.user.entity.User;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * Truy cập người dùng đang đăng nhập.
 *
 * <p>Đây là nguồn duy nhất để lấy {@code userId} trong các API của khách hàng.
 * <b>Không</b> nhận {@code userId} từ query param/body, vì như vậy sẽ cho phép
 * người dùng thao tác trên dữ liệu của người khác (IDOR).
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public UserPrincipal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    public Long getCurrentUserId() {
        return getCurrentPrincipal().getId();
    }

    public String getCurrentUsername() {
        return getCurrentPrincipal().getUsername();
    }

    /** Bản entity đầy đủ, dùng khi cần thao tác ghi. */
    public User getCurrentUser() {
        return userRepository.findById(getCurrentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}
