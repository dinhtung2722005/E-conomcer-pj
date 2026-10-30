package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderStatus;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.service.InventoryService;
import com.example.demo.user.exception.*;
import com.example.demo.user.service.CurrentUserService;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Service @Validated @RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class WalletPaymentService {
    private final OrderRepository orders;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds;
    private final WalletOperationService operations;
    private final WalletPinService pins;
    private final InventoryService inventory;
    private final CurrentUserService currentUser;
    private final PlatformTransactionManager transactionManager;
    private final WalletLedgerService ledger;
    private final WalletPaymentPrerequisites prerequisites;

    public PaymentResponse payOrder(@NotNull @Positive Long orderId, @NotNull @Valid PayOrderRequest request, String key) {
        var user = currentUser.getCurrentUser();
        if (!user.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        String fingerprint = operations.fingerprint("PAYMENT|" + orderId + "|" + request.isUseBonus());
        var authorization = pins.authorize(request.getPin(), fingerprint);
        return new TransactionTemplate(transactionManager).execute(status -> {
            pins.lockAuthorizingUser(authorization);
            // All order money paths use Order -> Wallet -> security/transactions -> holds -> inventory.
            Order order = orders.findByIdAndUserIdForUpdate(orderId, user.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
            Wallet wallet = wallets.findByUserIdForUpdate(user.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
            pins.requireAuthorization(authorization, wallet, fingerprint);
            var existing = operations.find(wallet, key, fingerprint);
            if (existing.isPresent()) return response(orderId, wallet.getCurrency(), existing.get());
            if (order.getStatus() != OrderStatus.PENDING) throw new AppException(ErrorCode.ORDER_STATUS_INVALID);
            if (wallet.getStatus() != WalletStatus.ACTIVE) throw new AppException(ErrorCode.WALLET_LOCKED);
            prerequisites.validate(order, wallet.getCurrency());
            BigDecimal total = order.getFinalAmount();
            if (total == null || total.signum() < 0) throw new AppException(ErrorCode.INVALID_REQUEST);
            BigDecimal bonus = request.isUseBonus() ? wallet.getBonusBalance().min(total) : BigDecimal.ZERO;
            BigDecimal cash = total.subtract(bonus);
            if (wallet.getBalance().compareTo(cash) < 0) throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
            WalletOperation operation = operations.create(wallet, key, fingerprint, TransactionType.PAYMENT, orderId.toString());
            reserve(order, wallet, operation, WalletBalanceType.CASH, cash);
            reserve(order, wallet, operation, WalletBalanceType.BONUS, bonus);
            for (var item : order.getItems().stream().sorted(Comparator.comparing(value -> value.getProduct().getId())).toList()) {
                inventory.confirmSale(item.getProduct().getId(), item.getQuantity());
            }
            order.setStatus(OrderStatus.PAID);
            orders.saveAndFlush(order);
            return response(orderId, wallet.getCurrency(), operation);
        });
    }

    private void reserve(Order order, Wallet wallet, WalletOperation operation, WalletBalanceType source, BigDecimal amount) {
        if (amount.signum() == 0) return;
        ledger.open(wallet);
        var hold = holds.saveAndFlush(WalletHold.builder().wallet(wallet).order(order).referenceId(order.getId().toString())
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
