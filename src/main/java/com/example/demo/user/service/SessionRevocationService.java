package com.example.demo.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.entity.User;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.repository.RefreshTokenRepository;
import com.example.demo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SessionRevocationService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /** Luôn khóa user trước refresh token, cùng thứ tự với đăng nhập và refresh. */
    @Transactional
    public void revokeAllSessions(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.invalidateSessions();
        userRepository.save(user);
        refreshTokenRepository.deleteByUser_Id(userId);
    }
}
