package com.example.demo.wallet.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.example.demo.shared.web.PageResponse;
import com.example.demo.user.api.UserAccount;
import com.example.demo.user.api.UserRole;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.api.UserAccountsApi;

import com.example.demo.wallet.api.dto.WalletFreezeRequest;
import com.example.demo.wallet.api.dto.WalletHistoryQuery;
import com.example.demo.wallet.api.dto.WalletHoldQuery;
import com.example.demo.wallet.api.dto.WalletHoldResponse;
import com.example.demo.wallet.api.dto.WalletResponse;
import com.example.demo.wallet.api.dto.WalletTransactionResponse;
import com.example.demo.wallet.entity.Wallet;
import com.example.demo.wallet.api.WalletStatus;
import com.example.demo.wallet.entity.WalletStatusChange;
import com.example.demo.wallet.mapper.WalletMapper;
import com.example.demo.wallet.repository.WalletHoldRepository;
import com.example.demo.wallet.repository.WalletRepository;
import com.example.demo.wallet.repository.WalletSecurityRepository;
import com.example.demo.wallet.repository.WalletStatusChangeRepository;
import com.example.demo.wallet.repository.WalletTransactionRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service
@Validated
@RequiredArgsConstructor
public class WalletService implements com.example.demo.wallet.api.WalletAccountsApi {
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletHoldRepository holdRepository;
    private final WalletSecurityRepository securityRepository;
    private final WalletStatusChangeRepository statusChangeRepository;
    private final UserAccountsApi userRepository;
    private final UserAccountsApi currentUserService;
    private final WalletMapper mapper;

    /** Trusted application use (e.g. registration). Never bind the userId argument from a customer request. */
    @Transactional
    public WalletResponse initializeWallet(Long userId) {
        lockActiveUser(userId);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> walletRepository.saveAndFlush(Wallet.builder().userId(userId).build()));
        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse initializeMyWallet() {
        // The outer transaction also covers this same-bean call.
        return initializeWallet(currentUserService.getCurrentUserId());
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet() {
        return toResponse(findMyWallet());
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getHistory(@NotNull @Valid WalletHistoryQuery query) {
        Wallet wallet = findMyWallet();
        return PageResponse.from(transactionRepository.findHistory(wallet.getId(), query.getType(), query.getStatus(),
                query.getFrom(), query.getTo(), query.toPageable()).map(mapper::toTransactionResponse));
    }

    @Transactional(readOnly = true)
    public WalletTransactionResponse getTransaction(@NotNull @Positive Long transactionId) {
        Wallet wallet = findMyWallet();
        return transactionRepository.findByIdAndWalletId(transactionId, wallet.getId())
                .map(mapper::toTransactionResponse)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_TRANSACTION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletHoldResponse> getHolds(@NotNull @Valid WalletHoldQuery query) {
        Wallet wallet = findMyWallet();
        return PageResponse.from(holdRepository.findByWalletIdAndStatusInOrderByCreatedAtDescIdDesc(wallet.getId(),
                query.getStatuses(), query.toPageable()).map(mapper::toHoldResponse));
    }

    @Transactional
    public WalletResponse freezeMyWallet(@NotNull @Valid WalletFreezeRequest request) {
        UserAccount actor = lockActiveUser(currentUserService.getCurrentUserId());
        Wallet wallet = walletRepository.findByUserIdForUpdate(actor.getId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        return changeStatus(wallet, WalletStatus.LOCKED, actor, request.getReason());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WalletResponse freezeWallet(@NotNull @Positive Long walletId, @NotNull @Valid WalletFreezeRequest request) {
        UserAccount actor = lockActiveAdministrator();
        return changeStatus(findWalletForUpdate(walletId), WalletStatus.LOCKED, actor, request.getReason());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public WalletResponse unfreezeWallet(@NotNull @Positive Long walletId, @NotNull @Valid WalletFreezeRequest request) {
        UserAccount actor = lockActiveAdministrator();
        return changeStatus(findWalletForUpdate(walletId), WalletStatus.ACTIVE, actor, request.getReason());
    }

    private Wallet findMyWallet() {
        UserAccount user = currentUserService.getCurrentUser();
        requireActive(user);
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
    }

    private Wallet findWalletForUpdate(Long walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
    }

    private UserAccount lockActiveUser(Long userId) {
        UserAccount user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        requireActive(user);
        return user;
    }

    private UserAccount lockActiveAdministrator() {
        UserAccount actor = lockActiveUser(currentUserService.getCurrentUserId());
        if (actor.getRole() != UserRole.ADMIN) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    private void requireActive(UserAccount user) {
        if (!user.isActive()) {
            throw new AppException(ErrorCode.USER_LOCKED);
        }
    }

    private WalletResponse changeStatus(Wallet wallet, WalletStatus target, UserAccount actor, String reason) {
        if (wallet.getStatus() == target) {
            return toResponse(wallet);
        }
        WalletStatus previous = wallet.getStatus();
        wallet.setStatus(target);
        walletRepository.saveAndFlush(wallet);
        statusChangeRepository.saveAndFlush(WalletStatusChange.builder().wallet(wallet).actorId(actor.getId())
                .actorRole(actor.getRole()).previousStatus(previous).newStatus(target).reason(reason.strip()).build());
        return toResponse(wallet);
    }

    private WalletResponse toResponse(Wallet wallet) {
        boolean pinConfigured = securityRepository.findById(wallet.getId())
                .map(security -> security.getPinHash() != null && !security.getPinHash().isBlank()).orElse(false);
        return mapper.toWalletResponse(wallet, pinConfigured);
    }
}
