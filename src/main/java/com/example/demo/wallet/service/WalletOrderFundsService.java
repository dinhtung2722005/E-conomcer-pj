package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;

/** Internal order lifecycle hooks; caller locks the order and keeps stock/voucher changes in this transaction. */
@Service @RequiredArgsConstructor
public class WalletOrderFundsService {
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds;

    @Transactional(propagation = Propagation.MANDATORY)
    public void refundCancellation(Order order) {
        var wallet = wallets.findByUserIdForUpdate(order.getUserId()).orElse(null);
        // Legacy orders without wallet payments do not mint a wallet refund.
        if (wallet == null) return;
        for (var payment : transactions.findOrderPaymentsForUpdate(wallet.getId(), order.getId().toString())) {
            var related = transactions.findRelatedTransactionsForUpdate(payment.getId(), TransactionType.REFUND);
            BigDecimal refunded = related.stream().filter(value -> value.getStatus() == TransactionStatus.SUCCESS
                    || value.getStatus() == TransactionStatus.PENDING || value.getStatus() == TransactionStatus.PROCESSING)
                    .map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = payment.getAmount().subtract(refunded);
            if (amount.signum() == 0) continue;
            var hold = holds.findByIdAndWalletIdForUpdate(payment.getHold().getId(), wallet.getId()).orElseThrow();
            if (amount.signum() < 0 || hold.getRemainingAmount().compareTo(amount) < 0) {
                throw new IllegalStateException("Payment refund requires settlement compensation");
            }
            hold.release(amount);
            if (payment.getBalanceType() == WalletBalanceType.CASH) {
                wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
                wallet.setBalance(wallet.getBalance().add(amount));
            } else {
                wallet.setPendingBonusBalance(wallet.getPendingBonusBalance().subtract(amount));
                wallet.setBonusBalance(wallet.getBonusBalance().add(amount));
            }
            transactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).referenceId(payment.getReferenceId())
                    .idempotencyKey("cancel:" + payment.getId()).type(TransactionType.REFUND)
                    .balanceType(payment.getBalanceType()).amount(amount).originalTransaction(payment)
                    .status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());
        }
    }
}
