package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;

/** Internal settlement. Order lock serializes return decisions, cancellation and settlement. */
@Service @RequiredArgsConstructor
public class WalletSettlementService {
    private final OrderRepository orders;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds;
    private final WalletRefundCaseRepository refunds;
    private final WalletLedgerService ledger;
    private final WalletRefundLineRepository refundLines;
    private final WalletDebtService debts;

    @Transactional
    public boolean settle(Long orderId) {
        var order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.COMPLETED || order.getSettledAt() != null
                || order.getSettlementAvailableAt() == null || order.getSettlementAvailableAt().isAfter(LocalDateTime.now())) return false;
        if (refunds.findFirstByOrderIdOrderByIdDesc(orderId).filter(row -> row.getStatus() == WalletRefundCase.Status.REQUESTED).isPresent()) return false;
        var returned=refundLines.approved(orderId);
        Map<Long,BigDecimal> returnedAmounts=new HashMap<>(),returnedFees=new HashMap<>();
        returned.forEach(l->{returnedAmounts.merge(l.getItem().getId(),l.getAmount(),BigDecimal::add);returnedFees.merge(l.getItem().getId(),l.getFeeAmount(),BigDecimal::add);});
        BigDecimal due=order.getFinalAmount().subtract(order.getRefundedAmount());
        Set<Long> userIds = new HashSet<>(); userIds.add(order.getUserId());
        BigDecimal allocated = BigDecimal.ZERO;
        Map<Long, BigDecimal> sellerAmounts = new TreeMap<>();
        BigDecimal fees = BigDecimal.ZERO;
        for (var item : order.getItems()) {
            BigDecimal share=item.getSettlementAmount().subtract(returnedAmounts.getOrDefault(item.getId(),BigDecimal.ZERO));
            BigDecimal fee=item.getPlatformFee().subtract(returnedFees.getOrDefault(item.getId(),BigDecimal.ZERO));
            if(share.signum()<0||fee.signum()<0||fee.compareTo(share)>0)throw new IllegalStateException("Refund allocation exceeds order snapshot");
            if (share.signum() == 0) continue;
            if (item.getSellerId() == null || item.getSettlementAmount().signum() < 0 || item.getPlatformFee().signum() < 0
                    || item.getPlatformFee().compareTo(item.getSettlementAmount()) > 0) {
                throw new IllegalStateException("Order has no valid seller allocation snapshot");
            }
            allocated = allocated.add(share); fees = fees.add(fee);
            userIds.add(item.getSellerId());
            sellerAmounts.merge(item.getSellerId(), share.subtract(fee), BigDecimal::add);
        }
        if (allocated.compareTo(due) != 0) throw new IllegalStateException("Order settlement allocation does not balance");
        var locked = wallets.findAllByIdInForUpdate(wallets.findIdsByUserIds(userIds));
        Map<Long, Wallet> byUser = new HashMap<>(); locked.forEach(wallet -> byUser.put(wallet.getUserId(), wallet));
        if (byUser.size() != userIds.size()) throw new IllegalStateException("A settlement wallet is missing");
        Wallet buyer = byUser.get(order.getUserId());
        for (var wallet : locked) {
            if (!wallet.getCurrency().equals(buyer.getCurrency())) throw new IllegalStateException("Settlement currencies differ");
        }
        var payments = transactions.findOrderPaymentsForUpdate(buyer.getId(), orderId.toString());
        if (payments.stream().map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(order.getFinalAmount()) != 0) throw new IllegalStateException("Settlement payment proof does not balance");
        locked.forEach(ledger::open);
        Map<String, BigDecimal> postings = new HashMap<>();
        for (var payment : payments) {
            var hold = holds.findByIdAndWalletIdForUpdate(payment.getHold().getId(), buyer.getId()).orElseThrow();
            BigDecimal refunded=transactions.findRelatedTransactionsForUpdate(payment.getId(),TransactionType.REFUND).stream().filter(t->t.getStatus()==TransactionStatus.SUCCESS).map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal capture=payment.getAmount().subtract(refunded);
            if (hold.getRemainingAmount().compareTo(capture) != 0) throw new IllegalStateException("Settlement hold is not intact");
            if(capture.signum()==0)continue;
            hold.capture(capture);
            if (payment.getBalanceType() == WalletBalanceType.CASH) {
                buyer.setPendingBalance(buyer.getPendingBalance().subtract(capture));
            } else buyer.setPendingBonusBalance(buyer.getPendingBonusBalance().subtract(capture));
            postings.merge(ledger.account(buyer, payment.getBalanceType(), true), capture.negate(), BigDecimal::add);
        }
        sellerAmounts.forEach((userId, amount) -> {
            if (amount.signum() == 0) return;
            Wallet seller = byUser.get(userId);
            seller.setBalance(seller.getBalance().add(amount));
            postings.merge(ledger.account(seller, WalletBalanceType.CASH, false), amount, BigDecimal::add);
            transactions.saveAndFlush(WalletTransaction.builder().wallet(seller).referenceId(orderId.toString())
                    .idempotencyKey("settlement:" + orderId).type(TransactionType.SETTLEMENT).amount(amount)
                    .status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());
            BigDecimal repayment=debts.collect(seller,WalletBalanceType.CASH,amount);
            if(repayment.signum()>0){
                seller.setBalance(seller.getBalance().subtract(repayment));postings.merge(ledger.account(seller,WalletBalanceType.CASH,false),repayment.negate(),BigDecimal::add);postings.merge("platform:REFUND_RESERVE",repayment,BigDecimal::add);
                transactions.saveAndFlush(WalletTransaction.builder().wallet(seller).referenceId(orderId.toString()).idempotencyKey("debt-repay:"+orderId).type(TransactionType.DEBT_REPAYMENT).amount(repayment).status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());
            }
        });
        if (fees.signum() > 0) postings.merge("platform:FEES", fees, BigDecimal::add);
        if (due.signum() > 0) ledger.post("settlement:" + orderId, buyer.getCurrency(), postings);
        // Reward only after the return window, so ordinary returns cannot spend a reward then demand it back.
        BigDecimal cashback = due.multiply(order.getCashbackPercent())
                .divide(new BigDecimal("100"), 4, RoundingMode.DOWN);
        if (cashback.signum() > 0) {
            buyer.setBonusBalance(buyer.getBonusBalance().add(cashback));
            var grant=transactions.saveAndFlush(WalletTransaction.builder().wallet(buyer).referenceId(orderId.toString())
                    .idempotencyKey("cashback:" + orderId).type(TransactionType.CASHBACK).balanceType(WalletBalanceType.BONUS)
                    .amount(cashback).status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());
            ledger.move("cashback:" + orderId, buyer.getCurrency(), "platform:PROMOTIONS",
                    ledger.account(buyer, WalletBalanceType.BONUS, false), cashback);
            BigDecimal recovered=debts.collect(buyer,WalletBalanceType.BONUS,cashback);
            if(recovered.signum()>0){buyer.setBonusBalance(buyer.getBonusBalance().subtract(recovered));transactions.saveAndFlush(WalletTransaction.builder().wallet(buyer).referenceId(orderId.toString()).idempotencyKey("bonus-debt:"+orderId).type(TransactionType.CASHBACK_REVERSAL).balanceType(WalletBalanceType.BONUS).amount(recovered).originalTransaction(grant).status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());ledger.move("bonus-debt:"+orderId,buyer.getCurrency(),ledger.account(buyer,WalletBalanceType.BONUS,false),"platform:PROMOTIONS",recovered);}
        }
        order.setSettledAmount(due);order.setSettledAt(LocalDateTime.now()); orders.saveAndFlush(order);
        order.setSettlementLastError(null); order.setSettlementNextAttemptAt(null);
        return true;
    }
}
