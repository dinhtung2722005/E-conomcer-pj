package com.example.demo.user.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.entity.RefreshToken;
import com.example.demo.user.entity.User;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.RefreshTokenRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.JwtUtils;

import lombok.RequiredArgsConstructor;

/**
 * Quản lý refresh token.
 *
 * <p>Mỗi người dùng chỉ có một refresh token tại một thời điểm. Mỗi lần refresh
 * token cũ bị xóa và thay bằng token mới (rotation) — nhờ vậy token bị đánh cắp
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
        // Phải flush DELETE trước khi INSERT, nếu không Hibernate sẽ chèn bản ghi mới
        // trước và vi phạm ràng buộc duy nhất trên refresh_tokens.user_id.
        refreshTokenRepository.deleteByUser_Id(userId);
        refreshTokenRepository.flush();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(Instant.now().plusMillis(jwtUtils.getRefreshExpirationMs()));
        refreshToken.setToken(jwtUtils.generateRefreshTokenValue());

        return refreshTokenRepository.save(refreshToken);
    }

    /** Kiểm tra hạn và <b>xóa</b> token cũ, trả về người dùng sở hữu. Token chỉ dùng được một lần. */
    @Transactional
    public User consumeRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (refreshToken.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        User user = refreshToken.getUser();
        refreshTokenRepository.delete(refreshToken);
        return user;
    }

    @Transactional
    public void revokeByUserId(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }
}
