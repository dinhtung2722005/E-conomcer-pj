package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.api.OrderStatus;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.User;
import com.example.demo.user.api.UserRole;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.entity.Wallet;
import com.example.demo.wallet.api.WalletBalanceType;
import com.example.demo.wallet.entity.WalletHold;
import com.example.demo.wallet.api.WalletHoldStatus;
import com.example.demo.wallet.api.WalletHoldType;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.entity.WalletTransaction;
import com.example.demo.wallet.repository.WalletHoldRepository;
import com.example.demo.wallet.repository.WalletRepository;
import com.example.demo.wallet.repository.WalletSecurityRepository;
import com.example.demo.wallet.repository.WalletTransactionRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
@Transactional
class WalletRepositoryIntegrationTests {
    @Autowired WalletRepository wallets;
    @Autowired WalletTransactionRepository walletTransactions;
    @Autowired WalletHoldRepository holds;
    @Autowired WalletSecurityRepository security;
    @Autowired UserRepository users;
    @Autowired OrderRepository orders;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void configureTransactions() {
        transaction = new TransactionTemplate(transactionManager);
    }

    @Test
    void walletLookupsRespectOwnerAndMultiWalletLocksUseAscendingIds() {
        Wallet first = wallet();
        Wallet second = wallet();
        assertEquals(first.getId(), wallets.findByUserId(first.getUserId()).orElseThrow().getId());
        assertTrue(wallets.existsByUserId(first.getUserId()));
        assertTrue(wallets.findByIdAndUserId(first.getId(), second.getUserId()).isEmpty());
        assertTrue(wallets.findByIdAndUserIdForUpdate(first.getId(), second.getUserId()).isEmpty());
        assertEquals(first.getId(), wallets.findByUserIdForUpdate(first.getUserId()).orElseThrow().getId());
        assertEquals(first.getId(), wallets.findByIdAndUserIdForUpdate(first.getId(), first.getUserId()).orElseThrow().getId());
        assertEquals(List.of(first.getId(), second.getId()),
                wallets.findAllByIdInForUpdate(List.of(second.getId(), first.getId())).stream().map(Wallet::getId).toList());
    }

    @Test
    void historyFiltersWalletTypeStatusAndHalfOpenTimeRangeWithStablePagination() {
        Wallet owner = wallet();
        Wallet other = wallet();
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime to = from.plusHours(1);
        WalletTransaction first = deposit(owner, "first", TransactionStatus.SUCCESS);
        WalletTransaction second = deposit(owner, "second", TransactionStatus.SUCCESS);
        WalletTransaction excludedAtEnd = deposit(owner, "at-end", TransactionStatus.PENDING);
        WalletTransaction cashback = WalletTransaction.builder().wallet(owner).referenceId("business")
                .idempotencyKey("bonus").type(TransactionType.CASHBACK).balanceType(WalletBalanceType.BONUS)
                .status(TransactionStatus.SUCCESS).amount(BigDecimal.ONE).build();
        walletTransactions.saveAndFlush(cashback);
        WalletTransaction foreign = deposit(other, "foreign", TransactionStatus.SUCCESS);
        for (WalletTransaction item : List.of(first, second, cashback, foreign)) {
            createdAt(item, from);
        }
        createdAt(excludedAtEnd, to);
        entities.clear();

        assertEquals(2, walletTransactions.findHistory(owner.getId(), TransactionType.DEPOSIT, TransactionStatus.SUCCESS,
                null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(3, walletTransactions.findHistory(owner.getId(), null, null,
                from, to, PageRequest.of(0, 10)).getTotalElements());
        var page = walletTransactions.findHistory(owner.getId(), TransactionType.DEPOSIT, TransactionStatus.SUCCESS,
                from, to, PageRequest.of(0, 1));
        assertEquals(2, page.getTotalElements());
        assertEquals(second.getId(), page.getContent().getFirst().getId());
        assertEquals(first.getId(), walletTransactions.findHistory(owner.getId(), TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS, from, to, page.nextPageable()).getContent().getFirst().getId());
        assertEquals(4, walletTransactions.findHistory(owner.getId(), null, null, null, null,
                PageRequest.of(0, 10)).getTotalElements());
        assertTrue(walletTransactions.findByIdAndWalletId(first.getId(), other.getId()).isEmpty());
        assertTrue(walletTransactions.findByIdAndWalletIdForUpdate(first.getId(), other.getId()).isEmpty());
        assertEquals(List.of(first.getId(), second.getId(), excludedAtEnd.getId(), cashback.getId()),
                walletTransactions.findByWalletIdAndReferenceIdOrderByIdAsc(owner.getId(), "business")
                        .stream().map(WalletTransaction::getId).toList());
    }

    @Test
    void retryAndProviderLookupsSelectTheExactOperation() {
        Wallet owner = wallet();
        Wallet other = wallet();
        WalletTransaction first = deposit(owner, "same-key", TransactionStatus.PENDING);
        WalletTransaction second = deposit(other, "same-key", TransactionStatus.PENDING);
        first.setProvider("TEST_BANK");
        first.setProviderTransactionId("provider-operation");
        walletTransactions.saveAndFlush(first);
        assertEquals(first.getId(), walletTransactions.findByWalletIdAndIdempotencyKey(owner.getId(), "same-key")
                .orElseThrow().getId());
        assertEquals(second.getId(), walletTransactions.findByWalletIdAndIdempotencyKeyForUpdate(other.getId(), "same-key")
                .orElseThrow().getId());
        assertEquals(first.getId(), walletTransactions.findByProviderAndProviderTransactionId("TEST_BANK", "provider-operation")
                .orElseThrow().getId());
        assertEquals(first.getId(), walletTransactions.findByProviderAndProviderTransactionIdForUpdate("TEST_BANK", "provider-operation")
                .orElseThrow().getId());
        assertTrue(walletTransactions.findByProviderAndProviderTransactionId("OTHER_BANK", "provider-operation").isEmpty());
    }

    @Test
    void holdQueriesSeparateWalletsOrdersSourcesAndActiveStates() {
        Wallet owner = wallet();
        Wallet other = wallet();
        WalletHold cash = orderHold(owner, "order-reference");
        WalletHold bonus = holds.saveAndFlush(WalletHold.builder().wallet(owner).orderId(cash.getOrderId())
                .referenceId(cash.getReferenceId()).type(WalletHoldType.ORDER_PAYMENT)
                .balanceType(WalletBalanceType.BONUS).amount(new BigDecimal("25")).build());
        cash.capture(BigDecimal.TEN);
        holds.flush();
        assertEquals(List.of(cash.getId(), bonus.getId()), holds.findByWalletIdAndOrderIdForUpdate(owner.getId(),
                cash.getOrderId()).stream().map(WalletHold::getId).toList());
        assertEquals(2, holds.findByWalletIdAndOrderIdOrderByIdAsc(owner.getId(), cash.getOrderId()).size());
        assertTrue(holds.findByWalletIdAndOrderIdForUpdate(other.getId(), cash.getOrderId()).isEmpty());
        assertTrue(holds.findByIdAndWalletId(cash.getId(), other.getId()).isEmpty());
        assertTrue(holds.findByIdAndWalletIdForUpdate(cash.getId(), other.getId()).isEmpty());
        assertEquals(bonus.getId(), holds.findByWalletIdAndTypeAndReferenceIdAndBalanceType(owner.getId(),
                WalletHoldType.ORDER_PAYMENT, "order-reference", WalletBalanceType.BONUS).orElseThrow().getId());
        assertEquals(cash.getId(), holds.findByReferenceForUpdate(owner.getId(), WalletHoldType.ORDER_PAYMENT,
                "order-reference", WalletBalanceType.CASH).orElseThrow().getId());
        assertEquals(List.of(bonus.getId()), holds.findByWalletIdAndStatusInOrderByCreatedAtDescIdDesc(owner.getId(),
                List.of(WalletHoldStatus.ACTIVE), PageRequest.of(0, 10)).map(WalletHold::getId).getContent());
    }

    @Test
    void relatedRefundReadIncludesPendingAmountsAndExcludesOtherOriginalTransactions() {
        Wallet owner = wallet();
        WalletTransaction original = payment(owner, "original");
        WalletTransaction other = payment(owner, "other-original");
        refund(original, "pending", "10", TransactionStatus.PENDING);
        refund(original, "processing", "20", TransactionStatus.PROCESSING);
        refund(original, "success", "30", TransactionStatus.SUCCESS);
        refund(original, "failed", "40", TransactionStatus.FAILED);
        refund(original, "cancelled", "50", TransactionStatus.CANCELLED);
        refund(other, "other", "60", TransactionStatus.SUCCESS);

        walletTransactions.findByIdAndWalletIdForUpdate(original.getId(), owner.getId()).orElseThrow();
        List<WalletTransaction> related = walletTransactions.findRelatedTransactionsForUpdate(original.getId(), TransactionType.REFUND);
        assertEquals(5, related.size());
        Set<TransactionStatus> reserved = Set.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING, TransactionStatus.SUCCESS);
        BigDecimal total = related.stream().filter(item -> reserved.contains(item.getStatus()))
                .map(WalletTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("60").compareTo(total));
        assertTrue(walletTransactions.findRelatedTransactionsForUpdate(original.getId(), TransactionType.CASHBACK_REVERSAL).isEmpty());
    }

    @Test
    void reconciliationScanIsBoundedAndOmitsTerminalAndRecentOperations() {
        Wallet owner = wallet();
        LocalDateTime cutoff = LocalDateTime.of(2026, 1, 2, 0, 0);
        WalletTransaction pending = deposit(owner, "pending", TransactionStatus.PENDING);
        WalletTransaction processing = deposit(owner, "processing", TransactionStatus.PROCESSING);
        WalletTransaction failed = deposit(owner, "failed", TransactionStatus.FAILED);
        WalletTransaction recent = deposit(owner, "recent", TransactionStatus.PENDING);
        for (WalletTransaction item : List.of(pending, processing, failed)) {
            createdAt(item, cutoff.minusDays(1));
        }
        createdAt(recent, cutoff);
        var statuses = List.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING);
        var first = walletTransactions.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(statuses, cutoff, PageRequest.of(0, 1));
        assertEquals(pending.getId(), first.getContent().getFirst().getId());
        assertTrue(first.hasNext());
        var second = walletTransactions.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAscIdAsc(statuses, cutoff, first.nextPageable());
        assertEquals(processing.getId(), second.getContent().getFirst().getId());
        assertFalse(second.hasNext());
    }

    @Test
    void securityLookupUsesWalletPrimaryKeyAndKeepsOtherWalletsSeparate() {
        Wallet owner = wallet();
        Wallet other = wallet();
        security.saveAndFlush(WalletSecurity.builder().wallet(owner).pinHash("test-only-hash").build());
        WalletSecurity locked = security.findByWalletIdForUpdate(owner.getId()).orElseThrow();
        locked.setFailedPinAttempts(1);
        security.flush();
        entities.clear();
        assertEquals(1, security.findById(owner.getId()).orElseThrow().getFailedPinAttempts());
        assertTrue(security.findByWalletIdForUpdate(other.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void walletRowLockSerializesTwoConcurrentSpends() throws Exception {
        Long id = transaction.execute(status -> wallet().getId());
        CountDownLatch ready = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> trySpend(id, ready));
            var second = executor.submit(() -> trySpend(id, ready));
            int successful = (first.get(15, TimeUnit.SECONDS) ? 1 : 0) + (second.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
            assertEquals(0, new BigDecimal("20").compareTo(wallets.findById(id).orElseThrow().getBalance()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean trySpend(Long walletId, CountDownLatch ready) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            ready.countDown();
            try {
                if (!ready.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent requests did not start in time");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            Wallet locked = wallets.findByIdForUpdate(walletId).orElseThrow();
            BigDecimal amount = new BigDecimal("80");
            if (locked.getBalance().compareTo(amount) < 0) {
                return false;
            }
            locked.setBalance(locked.getBalance().subtract(amount));
            wallets.saveAndFlush(locked);
            return true;
        }));
    }

    private Wallet wallet() {
        String suffix = UUID.randomUUID().toString();
        User user = users.saveAndFlush(User.builder().username("repo-" + suffix).email(suffix + "@example.test")
                .password("test-only-password").role(UserRole.CUSTOMER).build());
        return wallets.saveAndFlush(Wallet.builder().userId(user.getId()).balance(new BigDecimal("100")).build());
    }

    private WalletTransaction deposit(Wallet wallet, String key, TransactionStatus status) {
        return walletTransactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).referenceId("business")
                .idempotencyKey(key).type(TransactionType.DEPOSIT).status(status).amount(new BigDecimal("100")).build());
    }

    private WalletHold orderHold(Wallet wallet, String reference) {
        Order order = orders.saveAndFlush(Order.builder().userId(wallet.getUserId()).receiverName("Test Buyer")
                .receiverPhone("0900000000").shippingAddress("Test address").status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("100")).finalAmount(new BigDecimal("100")).build());
        return holds.saveAndFlush(WalletHold.builder().wallet(wallet).orderId(order.getId()).referenceId(reference)
                .type(WalletHoldType.ORDER_PAYMENT).amount(new BigDecimal("100")).build());
    }

    private WalletTransaction payment(Wallet wallet, String key) {
        WalletHold hold = orderHold(wallet, key);
        return walletTransactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).referenceId(key)
                .idempotencyKey(key).type(TransactionType.PAYMENT).hold(hold).status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("100")).build());
    }

    private void refund(WalletTransaction original, String key, String amount, TransactionStatus status) {
        walletTransactions.saveAndFlush(WalletTransaction.builder().wallet(original.getWallet())
                .referenceId(original.getReferenceId()).idempotencyKey(key).type(TransactionType.REFUND)
                .originalTransaction(original).status(status).amount(new BigDecimal(amount)).build());
    }

    private void createdAt(WalletTransaction item, LocalDateTime time) {
        // Use the same temporal mapping as repository parameters, independent of JDBC/session timezone conversion.
        entities.createQuery("UPDATE WalletTransaction t SET t.createdAt = :time WHERE t.id = :id")
                .setParameter("time", time).setParameter("id", item.getId()).executeUpdate();
    }
}
