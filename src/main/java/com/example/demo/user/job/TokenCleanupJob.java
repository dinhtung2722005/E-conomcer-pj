package com.example.demo.user.job;

import java.util.Date;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.repository.InvalidatedTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dọn bảng blacklist: các token đã hết hạn không còn tác dụng chặn nữa
 * và nếu giữ lại sẽ làm bảng phình to vô hạn.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {

    private final InvalidatedTokenRepository invalidatedTokenRepository;

    @Scheduled(initialDelayString = "${app.token.cleanup-initial-delay-ms:600000}",
            fixedDelayString = "${app.token.cleanup-interval-ms:3600000}")
    @Transactional
    public void purgeExpiredTokens() {
        long removed = invalidatedTokenRepository.deleteByExpiryTimeBefore(new Date());
        if (removed > 0) {
            log.info("Đã dọn {} token hết hạn khỏi blacklist", removed);
        }
    }
}
