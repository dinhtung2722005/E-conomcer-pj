package com.example.demo.user.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.entity.RefreshToken;
import com.example.demo.user.entity.User;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.repository.RefreshTokenRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.JwtUtils;

import lombok.RequiredArgsConstructor;

/**
 * Quản lý refresh token.
 *
 * <p>Mỗi người dùng chỉ có một refresh token tại một thời điểm. Mỗi lần refresh
 * giá trị token cũ được thay bằng token mới trong một transaction (rotation) — nhờ vậy token bị đánh cắp
 * chỉ dùng được một lần rồi vô hiệu, và request hợp lệ tiếp theo của nạn nhân
 * sẽ thất bại, giúp phát hiện bất thường.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        User user = lockUser(userId);
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_LOCKED);
        }
        // Phải flush DELETE trước khi INSERT, nếu không Hibernate sẽ chèn bản ghi mới
        // trước và vi phạm ràng buộc duy nhất trên refresh_tokens.user_id.
        refreshTokenRepository.deleteByUser_Id(userId);
        refreshTokenRepository.flush();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(jwtUtils.getRefreshExpirationMs()));
        refreshToken.setToken(jwtUtils.generateRefreshTokenValue());

        return refreshTokenRepository.save(refreshToken);
    }

    /** Khóa user rồi token, thay giá trị trong cùng transaction: token cũ chỉ dùng được một lần. */
    @Transactional
    public RefreshToken rotateRefreshToken(String token) {
        Long userId = refreshTokenRepository.findUserIdByToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
        User user = lockUser(userId);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!refreshToken.getExpiryDate().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_LOCKED);
        }
        refreshToken.setUser(user);
        refreshToken.setToken(jwtUtils.generateRefreshTokenValue());
        refreshToken.setExpiryDate(Instant.now().plusMillis(jwtUtils.getRefreshExpirationMs()));
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeByUserId(Long userId) {
        lockUser(userId);
        refreshTokenRepository.deleteByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}
