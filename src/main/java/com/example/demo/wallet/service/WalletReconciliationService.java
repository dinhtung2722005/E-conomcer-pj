package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.*;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.wallet.dto.WalletReconciliationResponse;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class WalletReconciliationService {
    private final WalletRepository wallets;
    private final WalletHoldRepository holds;
    private final WalletJournalRepository journals;
    private final WalletLedgerService ledger;
    private final CurrentUserService currentUser;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletReconciliationResponse inspectAsAdmin(Long walletId) {
        var user = currentUser.getCurrentUser();
        if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        if (user.getRole() != UserRole.ADMIN) throw new AppException(ErrorCode.ACCESS_DENIED);
        return inspectLocked(walletId);
    }

    /** Internal scheduled inspection; never exposed directly as an HTTP operation. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletReconciliationResponse inspect(Long walletId) { return inspectLocked(walletId); }

    private WalletReconciliationResponse inspectLocked(Long walletId) {
        Wallet wallet = wallets.findByIdForUpdate(walletId).orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        Map<String, BigDecimal> differences = new LinkedHashMap<>();
        if (wallet.isLedgerOpened()) {
            difference(differences, wallet, WalletBalanceType.CASH, false, wallet.getBalance());
            difference(differences, wallet, WalletBalanceType.CASH, true, wallet.getPendingBalance());
            difference(differences, wallet, WalletBalanceType.BONUS, false, wallet.getBonusBalance());
            difference(differences, wallet, WalletBalanceType.BONUS, true, wallet.getPendingBonusBalance());
        }
        var result = new WalletReconciliationResponse(); result.setWalletId(walletId);
        result.setLedgerInitialized(wallet.isLedgerOpened()); result.setLedgerDifferences(differences);
        result.setLedgerConsistent(wallet.isLedgerOpened() ? differences.values().stream().allMatch(value -> value.signum() == 0) : null);
        result.setCashHeldDifference(wallet.getPendingBalance().subtract(holds.sumRemaining(walletId, WalletBalanceType.CASH)));
        result.setBonusHeldDifference(wallet.getPendingBonusBalance().subtract(holds.sumRemaining(walletId, WalletBalanceType.BONUS)));
        result.setHoldsConsistent(result.getCashHeldDifference().signum() == 0 && result.getBonusHeldDifference().signum() == 0);
        result.setCheckedAt(LocalDateTime.now()); return result;
    }

    private void difference(Map<String, BigDecimal> differences, Wallet wallet, WalletBalanceType type, boolean held, BigDecimal actual) {
        differences.put(type + (held ? "_HELD" : "_AVAILABLE"), actual.subtract(journals.accountBalance(ledger.account(wallet, type, held), wallet.getCurrency())));
    }
}
