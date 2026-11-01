package com.example.demo.wallet.service;
import com.example.demo.wallet.api.TransactionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.WalletOperationRepository;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class WalletOperationService {
    private final WalletOperationRepository operations;

    /** Caller must hold the wallet write lock, including when no operation exists yet. */
    public Optional<WalletOperation> find(Wallet wallet, String key, String fingerprint) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,80}")) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        var existing = operations.findByWalletIdAndRequestKey(wallet.getId(), key);
        existing.ifPresent(operation -> {
            if (!operation.getFingerprint().equals(fingerprint)) {
                throw new AppException(ErrorCode.WALLET_IDEMPOTENCY_CONFLICT);
            }
        });
        return existing;
    }

    public WalletOperation create(Wallet wallet, String key, String fingerprint, TransactionType type, String reference) {
        return operations.saveAndFlush(WalletOperation.builder().wallet(wallet).requestKey(key)
                .fingerprint(fingerprint).type(type).reference(reference).build());
    }

    public String fingerprint(String canonicalPayload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
