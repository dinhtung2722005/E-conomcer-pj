package com.example.demo.user.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.entity.RefreshToken;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.RefreshTokenRepository;
import com.example.demo.user.repository.UserRepository;
@Service
public class RefreshTokenService {
       @Value("${app.jwt.refreshExpiration}")
       private Long refreshTokenDurationMs;
       private final RefreshTokenRepository refreshTokenRepository;
       private final UserRepository userRepository;
       public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
           this.refreshTokenRepository = refreshTokenRepository;
           this.userRepository = userRepository;
       }
       @Transactional
       public RefreshToken createRefreshToken(Long userId) {
        deleteByUserId(userId);
        userRepository.flush(); 
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUser(userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USERNOTFOUND)));
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString()); 

        return refreshTokenRepository.save(refreshToken);
    }
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        return token;
    }
    @Transactional
    public int deleteByUserId(Long userId) {
        return refreshTokenRepository.deleteByUser_Id(userId);
    }

    
}
