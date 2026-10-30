package com.example.demo.wallet.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.example.demo.user.entity.User;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.wallet.dto.ChangePinRequest;
import com.example.demo.wallet.dto.SetupPinRequest;
import com.example.demo.wallet.dto.WalletSecurityResponse;
import com.example.demo.wallet.entity.Wallet;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.mapper.WalletMapper;
import com.example.demo.wallet.repository.WalletRepository;
import com.example.demo.wallet.repository.WalletSecurityRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** PIN attempts commit in a short, independent transaction, before any money locks. */
@Service
@Validated
public class WalletPinService {
    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletSecurityRepository security;
    private final CurrentUserService currentUser;
    private final PasswordEncoder encoder;
    private final WalletMapper mapper;
    private final TransactionTemplate transaction;
    private final int maxAttempts;
    private final Duration lockDuration;

    public WalletPinService(UserRepository users, WalletRepository wallets, WalletSecurityRepository security,
            CurrentUserService currentUser, PasswordEncoder encoder, WalletMapper mapper,
            PlatformTransactionManager transactionManager,
            @Value("${wallet.pin.max-attempts:5}") int maxAttempts,
            @Value("${wallet.pin.lock-duration:PT15M}") Duration lockDuration) {
        if (maxAttempts < 1 || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("Invalid wallet PIN lock policy");
        }
        this.users = users;
        this.wallets = wallets;
        this.security = security;
        this.currentUser = currentUser;
        this.encoder = encoder;
        this.mapper = mapper;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public WalletSecurityResponse getMySecurity() {
        User user = currentUser.getCurrentUser();
        if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        Wallet wallet = wallets.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        return security.findById(wallet.getId()).map(mapper::toSecurityResponse)
                .orElseGet(WalletSecurityResponse::new);
    }

    public WalletSecurityResponse setupPin(@NotNull @Valid SetupPinRequest request) {
        return withSecurity(context -> {
            WalletSecurity row = context.security();
            if (row.getPinHash() != null) return Result.failure(ErrorCode.WALLET_PIN_ALREADY_CONFIGURED);
            if (isLocked(row)) return Result.failure(ErrorCode.WALLET_PIN_LOCKED);
            if (!encoder.matches(request.getCurrentPassword(), context.user().getPassword())) {
                return failedAttempt(row, ErrorCode.OLD_PASSWORD_INCORRECT);
            }
            changeHash(row, request.getNewPin());
            return Result.success(mapper.toSecurityResponse(row));
        });
    }

    public WalletSecurityResponse changePin(@NotNull @Valid ChangePinRequest request) {
        return withSecurity(context -> {
            WalletSecurity row = context.security();
            ErrorCode error = checkPin(row, request.getCurrentPin());
            if (error != null) return Result.failure(error);
            if (request.getCurrentPin().equals(request.getNewPin())) {
                return Result.failure(ErrorCode.WALLET_PIN_UNCHANGED);
            }
            changeHash(row, request.getNewPin());
            return Result.success(mapper.toSecurityResponse(row));
        });
    }

    /** Server-only authorization. Never expose this object as a client supplied token or boolean. */
    public Authorization authorize(@NotNull @Pattern(regexp = "[0-9]{6}") String pin,
            @NotNull String operation) {
        return withSecurity(context -> {
            WalletSecurity row = context.security();
            ErrorCode error = checkPin(row, pin);
            if (error != null) return Result.failure(error);
            security.flush();
            return Result.success(new Authorization(context.user().getId(), context.user().getTokenVersion(), context.wallet().getId(), row.getVersion(), operation,
                    LocalDateTime.now().plusMinutes(1)));
        });
    }

    /** Must be the first lock of a financial transaction: User -> Order -> Wallet. */
    void lockAuthorizingUser(Authorization authorization) {
        if (authorization == null || !currentUser.getCurrentUserId().equals(authorization.userId)) {
            throw new AppException(ErrorCode.WALLET_PIN_AUTHORIZATION_EXPIRED);
        }
        User user = users.findByIdForUpdate(authorization.userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        if (user.getTokenVersion() != authorization.tokenVersion) throw new AppException(ErrorCode.WALLET_PIN_AUTHORIZATION_EXPIRED);
    }

    /** Call after locking the wallet in the financial transaction, before changing balances. */
    void requireAuthorization(Authorization authorization, Wallet wallet, String operation) {
        WalletSecurity row = security.findByWalletIdForUpdate(wallet.getId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_PIN_NOT_CONFIGURED));
        if (authorization == null || !wallet.getId().equals(authorization.walletId)
                || !operation.equals(authorization.operation) || !row.getVersion().equals(authorization.version)
                || !LocalDateTime.now().isBefore(authorization.expiresAt) || isLocked(row)) {
            throw new AppException(ErrorCode.WALLET_PIN_AUTHORIZATION_EXPIRED);
        }
    }

    public static final class Authorization {
        private final Long userId;
        private final long tokenVersion;
        private final Long walletId;
        private final Long version;
        private final String operation;
        private final LocalDateTime expiresAt;

        private Authorization(Long userId, long tokenVersion, Long walletId, Long version, String operation, LocalDateTime expiresAt) {
            this.userId = userId;
            this.tokenVersion = tokenVersion;
            this.walletId = walletId;
            this.version = version;
            this.operation = operation;
            this.expiresAt = expiresAt;
        }
    }

    private ErrorCode checkPin(WalletSecurity row, String pin) {
        if (row.getPinHash() == null) return ErrorCode.WALLET_PIN_NOT_CONFIGURED;
        if (isLocked(row)) return ErrorCode.WALLET_PIN_LOCKED;
        if (!encoder.matches(pin, row.getPinHash())) {
            return failedAttempt(row, ErrorCode.WALLET_PIN_INCORRECT).error();
        }
        row.setFailedPinAttempts(0);
        row.setPinLockedUntil(null);
        return null;
    }

    private boolean isLocked(WalletSecurity row) {
        LocalDateTime until = row.getPinLockedUntil();
        if (until == null) return false;
        if (LocalDateTime.now().isBefore(until)) return true;
        row.setFailedPinAttempts(0);
        row.setPinLockedUntil(null);
        return false;
    }

    private <T> Result<T> failedAttempt(WalletSecurity row, ErrorCode error) {
        row.setFailedPinAttempts(row.getFailedPinAttempts() + 1);
        if (row.getFailedPinAttempts() >= maxAttempts) {
            row.setPinLockedUntil(LocalDateTime.now().plus(lockDuration));
            return Result.failure(ErrorCode.WALLET_PIN_LOCKED);
        }
        return Result.failure(error);
    }

    private void changeHash(WalletSecurity row, String pin) {
        row.setPinHash(encoder.encode(pin));
        row.setPinChangedAt(LocalDateTime.now());
        row.setFailedPinAttempts(0);
        row.setPinLockedUntil(null);
    }

    private <T> T withSecurity(Function<Context, Result<T>> action) {
        Long userId = currentUser.getCurrentUserId();
        Result<T> result = transaction.execute(status -> {
            User user = users.findByIdForUpdate(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
            if (user.getTokenVersion() != currentUser.getCurrentPrincipal().getTokenVersion()) {
                throw new AppException(ErrorCode.TOKEN_INVALID);
            }
            Wallet wallet = wallets.findByUserIdForUpdate(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
            WalletSecurity row = security.findByWalletIdForUpdate(wallet.getId())
                    .orElseGet(() -> security.saveAndFlush(WalletSecurity.builder().wallet(wallet).build()));
            return action.apply(new Context(user, wallet, row));
        });
        // Throw only after commit: rejecting a PIN must never roll back its failed-attempt counter.
        if (result.error() != null) throw new AppException(result.error());
        return result.value();
    }

    private record Context(User user, Wallet wallet, WalletSecurity security) {}
    private record Result<T>(T value, ErrorCode error) {
        static <T> Result<T> success(T value) { return new Result<>(value, null); }
        static <T> Result<T> failure(ErrorCode error) { return new Result<>(null, error); }
    }
}
