package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.wallet.api.dto.WalletFreezeRequest;
import com.example.demo.wallet.api.dto.WalletHistoryQuery;
import com.example.demo.wallet.api.dto.WalletHoldQuery;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.entity.Wallet;
import com.example.demo.wallet.api.WalletBalanceType;
import com.example.demo.wallet.entity.WalletHold;
import com.example.demo.wallet.api.WalletHoldStatus;
import com.example.demo.wallet.api.WalletHoldType;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.api.WalletStatus;
import com.example.demo.wallet.entity.WalletStatusChange;
import com.example.demo.wallet.entity.WalletTransaction;
import com.example.demo.wallet.repository.WalletHoldRepository;
import com.example.demo.wallet.repository.WalletRepository;
import com.example.demo.wallet.repository.WalletSecurityRepository;
import com.example.demo.wallet.repository.WalletStatusChangeRepository;
import com.example.demo.wallet.repository.WalletTransactionRepository;
import com.example.demo.wallet.service.WalletService;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
@Transactional
class WalletServiceIntegrationTests {
    @Autowired WalletService service;
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired WalletTransactionRepository transactions;
    @Autowired WalletHoldRepository holds;
    @Autowired WalletSecurityRepository security;
    @Autowired OrderRepository orders;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper json;
    @MockitoSpyBean WalletStatusChangeRepository audit;

    private TransactionTemplate databaseTransaction;

    @BeforeEach
    void setUp() {
        databaseTransaction = new TransactionTemplate(transactionManager);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void initializeMyWalletIsIdempotentAndDoesNotResetExistingMoneyOrPin() {
        User user = user(UserRole.CUSTOMER);
        authenticate(user);
        var created = service.initializeMyWallet();
        assertEquals(WalletStatus.ACTIVE, created.getStatus());
        assertEquals(0, created.getBalance().signum());
        assertFalse(created.isPinConfigured());
        Wallet wallet = wallets.findById(created.getId()).orElseThrow();
        wallet.setBalance(new BigDecimal("100"));
        wallet.setPendingBalance(new BigDecimal("20"));
        wallet.setBonusBalance(new BigDecimal("30"));
        wallet.setPendingBonusBalance(new BigDecimal("5"));
        wallet.setStatus(WalletStatus.LOCKED);
        wallets.saveAndFlush(wallet);
        security.saveAndFlush(WalletSecurity.builder().wallet(wallet).pinHash("test-only-hash").build());

        var existing = service.initializeMyWallet();
        assertEquals(created.getId(), existing.getId());
        assertEquals(WalletStatus.LOCKED, existing.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(existing.getBalance()));
        assertEquals(0, new BigDecimal("20").compareTo(existing.getPendingBalance()));
        assertEquals(0, new BigDecimal("30").compareTo(existing.getBonusBalance()));
        assertEquals(0, new BigDecimal("5").compareTo(existing.getPendingBonusBalance()));
        assertTrue(existing.isPinConfigured());
    }

    @Test
    void readsDoNotImplicitlyCreateWalletsAndAnonymousReadsFail() {
        assertError(ErrorCode.UNAUTHENTICATED, service::getMyWallet);
        User user = user(UserRole.CUSTOMER);
        authenticate(user);
        assertError(ErrorCode.WALLET_NOT_FOUND, service::getMyWallet);
        assertFalse(wallets.existsByUserId(user.getId()));
    }

    @Test
    void missingAndInactiveUsersCannotInitializeOrAccessWallet() {
        assertError(ErrorCode.USER_NOT_FOUND, () -> service.initializeWallet(Long.MAX_VALUE));
        User user = user(UserRole.CUSTOMER);
        service.initializeWallet(user.getId());
        user.setActive(false);
        users.saveAndFlush(user);
        authenticate(user);
        assertError(ErrorCode.USER_LOCKED, service::getMyWallet);
        assertError(ErrorCode.USER_LOCKED, () -> service.initializeWallet(user.getId()));
    }

    @Test
    void historyUsesOwnerFiltersExclusiveEndAndStableOneBasedPagination() {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        Wallet foreign = wallet(user(UserRole.CUSTOMER));
        WalletTransaction first = deposit(wallet, "first", TransactionStatus.SUCCESS);
        WalletTransaction second = deposit(wallet, "second", TransactionStatus.SUCCESS);
        WalletTransaction end = deposit(wallet, "end", TransactionStatus.PENDING);
        WalletTransaction outside = deposit(foreign, "foreign", TransactionStatus.SUCCESS);
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime to = from.plusHours(1);
        for (var item : List.of(first, second, outside)) {
            createdAt(item, from);
        }
        createdAt(end, to);
        entities.clear();
        authenticate(owner);
        WalletHistoryQuery query = new WalletHistoryQuery();
        query.setFrom(from);
        query.setTo(to);
        query.setType(TransactionType.DEPOSIT);
        query.setStatus(TransactionStatus.SUCCESS);
        query.setSize(1);
        var page = service.getHistory(query);
        assertEquals(1, page.getCurrentPage());
        assertEquals(2, page.getTotalPages());
        assertEquals(2, page.getTotalElements());
        assertEquals(second.getId(), page.getData().getFirst().getId());
        query.setPage(2);
        assertEquals(first.getId(), service.getHistory(query).getData().getFirst().getId());
    }

    @Test
    void transactionDetailsHideForeignIdsAndSensitiveProviderFields() {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        WalletTransaction failed = deposit(wallet, "own-key", TransactionStatus.FAILED);
        failed.setProvider("private-provider");
        failed.setProviderTransactionId("private-provider-id");
        failed.setFailureReason("private-raw-bank-error");
        failed.setDestinationAccountReference("private-bank-token");
        transactions.saveAndFlush(failed);
        WalletTransaction foreign = deposit(wallet(user(UserRole.CUSTOMER)), "foreign", TransactionStatus.SUCCESS);
        authenticate(owner);
        assertError(ErrorCode.WALLET_TRANSACTION_NOT_FOUND, () -> service.getTransaction(foreign.getId()));
        assertError(ErrorCode.WALLET_TRANSACTION_NOT_FOUND, () -> service.getTransaction(Long.MAX_VALUE));
        var response = service.getTransaction(failed.getId());
        assertEquals("Giao dịch không thành công", response.getFailureReason());
        String serialized = json.writeValueAsString(response);
        assertFalse(serialized.contains("private-"));
        assertFalse(serialized.contains("own-key"));
        assertFalse(serialized.contains("wallet"));
    }

    @Test
    void holdResponsesShowPartialAmountsAndFilterOnlyCurrentWallet() {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        WalletHold cash = orderHold(wallet, "cash");
        cash.capture(new BigDecimal("30"));
        cash.release(new BigDecimal("20"));
        holds.flush();
        orderHold(wallet, "active");
        orderHold(wallet(user(UserRole.CUSTOMER)), "foreign");
        authenticate(owner);
        WalletHoldQuery query = new WalletHoldQuery();
        assertEquals(2, service.getHolds(query).getTotalElements());
        query.setStatuses(Set.of(WalletHoldStatus.PARTIALLY_PROCESSED));
        var page = service.getHolds(query);
        assertEquals(1, page.getTotalElements());
        var response = page.getData().getFirst();
        assertEquals(cash.getId(), response.getId());
        assertEquals(cash.getOrderId(), response.getOrderId());
        assertEquals(0, new BigDecimal("50").compareTo(response.getRemainingAmount()));
        assertEquals(WalletBalanceType.CASH, response.getBalanceType());
    }

    @Test
    void invalidQueriesAndReasonsAreRejectedAtServiceBoundary() {
        User owner = user(UserRole.CUSTOMER);
        wallet(owner);
        authenticate(owner);
        WalletHistoryQuery history = new WalletHistoryQuery();
        history.setPage(0);
        assertThrows(ConstraintViolationException.class, () -> service.getHistory(history));
        history.setPage(1);
        history.setSize(101);
        assertThrows(ConstraintViolationException.class, () -> service.getHistory(history));
        history.setSize(10);
        history.setFrom(LocalDateTime.of(2026, 1, 1, 0, 0));
        history.setTo(history.getFrom());
        assertThrows(ConstraintViolationException.class, () -> service.getHistory(history));
        WalletHoldQuery holdQuery = new WalletHoldQuery();
        holdQuery.setStatuses(Set.of());
        assertThrows(ConstraintViolationException.class, () -> service.getHolds(holdQuery));
        assertThrows(ConstraintViolationException.class, () -> service.getHistory(null));
        assertThrows(ConstraintViolationException.class, () -> service.getTransaction(-1L));
        assertThrows(ConstraintViolationException.class, () -> service.freezeMyWallet(reason("   ")));
        assertThrows(ConstraintViolationException.class, () -> service.freezeMyWallet(reason("x".repeat(501))));
    }

    @Test
    void selfFreezeIsAuditedOnceDoesNotChangeMoneyAndStillAllowsReads() {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        authenticate(owner);
        var frozen = service.freezeMyWallet(reason("  Báo mất tài khoản  "));
        assertEquals(WalletStatus.LOCKED, frozen.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(frozen.getBalance()));
        service.freezeMyWallet(reason("Retry"));
        var changes = audit.findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), PageRequest.of(0, 10));
        assertEquals(1, changes.getTotalElements());
        var change = changes.getContent().getFirst();
        assertEquals(owner.getId(), change.getActorId());
        assertEquals(UserRole.CUSTOMER, change.getActorRole());
        assertEquals(WalletStatus.ACTIVE, change.getPreviousStatus());
        assertEquals(WalletStatus.LOCKED, change.getNewStatus());
        assertEquals("Báo mất tài khoản", change.getReason());
        assertNotNull(change.getCreatedAt());
        assertEquals(WalletStatus.LOCKED, service.getMyWallet().getStatus());
        assertEquals(0, service.getHistory(new WalletHistoryQuery()).getTotalElements());
    }

    @Test
    void customersCannotFreezeOtherWalletsOrUnfreezeEvenTheirOwn() {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        Wallet other = wallet(user(UserRole.CUSTOMER));
        authenticate(owner);
        assertThrows(AccessDeniedException.class, () -> service.freezeWallet(other.getId(), reason("forbidden")));
        service.freezeMyWallet(reason("self"));
        assertThrows(AccessDeniedException.class, () -> service.unfreezeWallet(wallet.getId(), reason("forbidden")));
        assertEquals(WalletStatus.ACTIVE, other.getStatus());
    }

    @Test
    void administratorsCanFreezeAndUnfreezeWithAnAuditForEachActualTransition() {
        Wallet wallet = wallet(user(UserRole.CUSTOMER));
        User admin = user(UserRole.ADMIN);
        authenticate(admin);
        service.freezeWallet(wallet.getId(), reason("Điều tra"));
        service.unfreezeWallet(wallet.getId(), reason("Đã xác minh"));
        service.unfreezeWallet(wallet.getId(), reason("Retry"));
        var changes = audit.findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), PageRequest.of(0, 10));
        assertEquals(2, changes.getTotalElements());
        assertEquals(WalletStatus.ACTIVE, changes.getContent().getFirst().getNewStatus());
        assertTrue(changes.stream().allMatch(change -> change.getActorId().equals(admin.getId())
                && change.getActorRole() == UserRole.ADMIN));
        assertEquals(WalletStatus.ACTIVE, wallets.findById(wallet.getId()).orElseThrow().getStatus());
        assertError(ErrorCode.WALLET_NOT_FOUND, () -> service.freezeWallet(Long.MAX_VALUE, reason("missing")));
    }

    @Test
    void staleAdministratorAuthorityCannotOverrideCurrentDatabaseRole() {
        User customer = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(customer);
        User stalePrincipal = User.builder().id(customer.getId()).username(customer.getUsername())
                .email(customer.getEmail()).password("test-only-password").role(UserRole.ADMIN).build();
        authenticate(stalePrincipal);
        assertError(ErrorCode.ACCESS_DENIED, () -> service.freezeWallet(wallet.getId(), reason("forbidden")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentInitializationReturnsTheSameWallet() throws Exception {
        Long userId = databaseTransaction.execute(status -> user(UserRole.CUSTOMER).getId());
        CountDownLatch ready = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> initializeTogether(userId, ready));
            var second = executor.submit(() -> initializeTogether(userId, ready));
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditWriteFailureRollsBackWalletStatus() {
        User owner = databaseTransaction.execute(status -> user(UserRole.CUSTOMER));
        Long walletId = databaseTransaction.execute(status -> wallet(owner).getId());
        authenticate(owner);
        doThrow(new DataIntegrityViolationException("test-only-audit-failure"))
                .when(audit).saveAndFlush(any(WalletStatusChange.class));
        assertThrows(DataIntegrityViolationException.class, () -> service.freezeMyWallet(reason("must rollback")));
        assertEquals(WalletStatus.ACTIVE, wallets.findById(walletId).orElseThrow().getStatus());
        assertEquals(0, audit.findByWalletIdOrderByCreatedAtDescIdDesc(walletId, PageRequest.of(0, 10)).getTotalElements());
    }

    private Long initializeTogether(Long userId, CountDownLatch ready) throws InterruptedException {
        ready.countDown();
        if (!ready.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent requests did not start in time");
        }
        return service.initializeWallet(userId).getId();
    }

    private User user(UserRole role) {
        String suffix = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().username("service-" + suffix).email(suffix + "@example.test")
                .password("test-only-password").role(role).build());
    }

    private Wallet wallet(User user) {
        return wallets.saveAndFlush(Wallet.builder().userId(user.getId()).balance(new BigDecimal("100")).build());
    }

    private WalletTransaction deposit(Wallet wallet, String key, TransactionStatus status) {
        return transactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).referenceId("business")
                .idempotencyKey(key).type(TransactionType.DEPOSIT).status(status).amount(new BigDecimal("100")).build());
    }

    private WalletHold orderHold(Wallet wallet, String reference) {
        Order order = orders.saveAndFlush(Order.builder().userId(wallet.getUserId()).receiverName("Test Buyer")
                .receiverPhone("0900000000").shippingAddress("Test address").status(OrderStatus.PAID)
                .totalAmount(new BigDecimal("100")).finalAmount(new BigDecimal("100")).build());
        return holds.saveAndFlush(WalletHold.builder().wallet(wallet).orderId(order.getId()).referenceId(reference)
                .type(WalletHoldType.ORDER_PAYMENT).amount(new BigDecimal("100")).build());
    }

    private WalletFreezeRequest reason(String text) {
        WalletFreezeRequest request = new WalletFreezeRequest();
        request.setReason(text);
        return request;
    }

    private void authenticate(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private void createdAt(WalletTransaction item, LocalDateTime time) {
        entities.createQuery("UPDATE WalletTransaction t SET t.createdAt = :time WHERE t.id = :id")
                .setParameter("time", time).setParameter("id", item.getId()).executeUpdate();
    }

    private void assertError(ErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(AppException.class, action::run).getErrorCode());
    }
}
