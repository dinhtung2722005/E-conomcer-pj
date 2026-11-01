package com.example.demo.wallet.service;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.api.WalletHoldType;
import com.example.demo.wallet.api.WalletStatus;
import com.example.demo.wallet.api.WalletBalanceType;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import com.example.demo.wallet.api.dto.PayOrderRequest;
import com.example.demo.wallet.api.dto.PaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.ecommerce.api.OrderSnapshot;
import com.example.demo.wallet.api.*;
import com.example.demo.ecommerce.api.OrderStatus;
import com.example.demo.shared.error.*;
import com.example.demo.user.api.UserAccountsApi;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class WalletPaymentService {
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds;
    private final WalletOperationService operations;
    private final WalletPinService pins;
    private final UserAccountsApi currentUser;
    private final WalletLedgerService ledger;
    private final WalletPaymentPrerequisites prerequisites;
    private final WalletRecoveryDebtRepository debts;
    private final WalletRiskService risks;

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public PaymentPermit authorizePayment(Long orderId,PayOrderRequest request){
        var user=currentUser.getCurrentUser();
        if(!user.isActive())throw new AppException(ErrorCode.USER_LOCKED);
        String fingerprint=operations.fingerprint("PAYMENT|"+orderId+"|"+request.isUseBonus());
        return new Permit(user.getId(),orderId,request.isUseBonus(),fingerprint,pins.authorize(request.getPin(),fingerprint));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void lockPayer(PaymentPermit value){
        var permit=permit(value);
        pins.lockAuthorizingUser(permit.authorization());
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new LockProof(permit));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public WalletPaymentsApi.Reservation reserve(OrderSnapshot order,PaymentPermit value,String key){
        var permit=permit(value);
        boolean payerLocked=org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                .stream().anyMatch(s->s instanceof LockProof proof&&proof.permit()==permit);
        if(!payerLocked||!currentUser.getCurrentUserId().equals(permit.userId()))throw new AppException(ErrorCode.WALLET_PIN_AUTHORIZATION_EXPIRED);
        if(!order.getId().equals(permit.orderId())||!order.getUserId().equals(permit.userId()))throw new AppException(ErrorCode.ACCESS_DENIED);
        Wallet wallet=wallets.findByUserIdForUpdate(permit.userId()).orElseThrow(()->new AppException(ErrorCode.WALLET_NOT_FOUND));
        pins.requireAuthorization(permit.authorization(),wallet,permit.fingerprint());
        var existing=operations.find(wallet,key,permit.fingerprint());
        if(existing.isPresent())return new WalletPaymentsApi.Reservation(response(order.getId(),wallet.getCurrency(),existing.get()),true);
        if(order.getStatus()!=OrderStatus.PENDING)throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
        if(wallet.getStatus()!=WalletStatus.ACTIVE)throw new AppException(ErrorCode.WALLET_LOCKED);
        if(!debts.outstanding(wallet.getUserId(),WalletBalanceType.CASH,wallet.getCurrency()).isEmpty())
            throw new AppException(ErrorCode.WALLET_DEBT_OUTSTANDING);
        prerequisites.validate(order,wallet.getCurrency());
        BigDecimal total=order.getFinalAmount();
        if(total==null||total.signum()<0)throw new AppException(ErrorCode.INVALID_REQUEST);
        BigDecimal bonus=permit.useBonus()?wallet.getBonusBalance().min(total):BigDecimal.ZERO;
        BigDecimal cash=total.subtract(bonus);
        if(wallet.getBalance().compareTo(cash)<0)throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        var operation=operations.create(wallet,key,permit.fingerprint(),TransactionType.PAYMENT,order.getId().toString());
        risks.reserve(wallet,operation,total);
        reserve(order,wallet,operation,WalletBalanceType.CASH,cash);
        reserve(order,wallet,operation,WalletBalanceType.BONUS,bonus);
        return new WalletPaymentsApi.Reservation(response(order.getId(),wallet.getCurrency(),operation),false);
    }
    private Permit permit(PaymentPermit value){
        if(!(value instanceof Permit permit))throw new AppException(ErrorCode.WALLET_PIN_AUTHORIZATION_EXPIRED);
        return permit;
    }
    private record Permit(Long userId,Long orderId,boolean useBonus,String fingerprint,WalletPinService.Authorization authorization) implements PaymentPermit{}
    private record LockProof(Permit permit) implements org.springframework.transaction.support.TransactionSynchronization{}

    private void reserve(OrderSnapshot order, Wallet wallet, WalletOperation operation, WalletBalanceType source, BigDecimal amount) {
        if (amount.signum() == 0) return;
        ledger.open(wallet);
        var hold = holds.saveAndFlush(WalletHold.builder().wallet(wallet).orderId(order.getId()).referenceId(order.getId().toString())
                .type(WalletHoldType.ORDER_PAYMENT).balanceType(source).amount(amount).build());
        if (source == WalletBalanceType.CASH) {
            wallet.setBalance(wallet.getBalance().subtract(amount));
            wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
        } else {
            wallet.setBonusBalance(wallet.getBonusBalance().subtract(amount));
            wallet.setPendingBonusBalance(wallet.getPendingBonusBalance().add(amount));
        }
        var transaction = transactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).operation(operation)
                .referenceId(order.getId().toString()).idempotencyKey("op:" + operation.getId() + ":" + source)
                .type(TransactionType.PAYMENT).balanceType(source).amount(amount).hold(hold)
                .status(TransactionStatus.SUCCESS).completedAt(java.time.LocalDateTime.now()).build());
        ledger.move("hold:" + transaction.getId(), wallet.getCurrency(), ledger.account(wallet, source, false),
                ledger.account(wallet, source, true), amount);
    }

    private PaymentResponse response(Long orderId, String currency, WalletOperation operation) {
        List<WalletTransaction> payments = transactions.findByOperationIdOrderByIdAsc(operation.getId());
        var result = new PaymentResponse();
        result.setOrderId(orderId); result.setCurrency(currency); result.setOrderStatus(OrderStatus.PAID);
        result.setCashAmount(payments.stream().filter(value -> value.getBalanceType() == WalletBalanceType.CASH)
                .map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setBonusAmount(payments.stream().filter(value -> value.getBalanceType() == WalletBalanceType.BONUS)
                .map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.setTotalAmount(result.getCashAmount().add(result.getBonusAmount()));
        result.setTransactionIds(payments.stream().map(WalletTransaction::getId).toList());
        result.setHoldIds(payments.stream().map(value -> value.getHold().getId()).toList());
        return result;
    }
}
