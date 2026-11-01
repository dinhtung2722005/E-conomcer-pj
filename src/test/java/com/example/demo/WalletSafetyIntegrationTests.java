package com.example.demo;
import com.example.demo.ecommerce.api.OrderWorkflowUseCase;
import com.example.demo.wallet.api.SettlementUseCase;
import com.example.demo.wallet.api.OrderPaymentUseCase;
import com.example.demo.ecommerce.service.WalletSettlementPolicy;
import com.example.demo.wallet.api.WalletHoldStatus;
import com.example.demo.wallet.api.WalletStatus;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import com.example.demo.wallet.api.dto.WithdrawRequest;
import com.example.demo.wallet.api.dto.WalletFreezeRequest;
import com.example.demo.wallet.api.dto.PayOrderRequest;
import com.example.demo.wallet.api.dto.LinkBankAccountRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;
import com.example.demo.ecommerce.api.OrderStatus;
import com.example.demo.ecommerce.entity.Product;
import com.example.demo.ecommerce.api.ProductStatus;
import com.example.demo.ecommerce.entity.Inventory;
import com.example.demo.ecommerce.repository.OrderRepository;
import com.example.demo.ecommerce.repository.ProductRepository;
import com.example.demo.ecommerce.repository.InventoryRepository;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.User;
import com.example.demo.user.api.UserRole;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.user.service.AdminUserService;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.gateway.BankGateway;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;

/** Regression tests for findings from the wallet audit. */
@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
@Import(WalletSafetyIntegrationTests.GatewayConfig.class)
class WalletSafetyIntegrationTests {
    @TestConfiguration static class GatewayConfig {
        @Bean ProbeBank probeBank() { return new ProbeBank(); }
    }
    static class ProbeBank implements BankGateway {
        final AtomicInteger submissions = new AtomicInteger();
        @Override public String provider() { return "AUDIT_TEST_BANK"; }
        @Override public VerifiedAccount verifyOwnership(Long userId, String proof) {
            return new VerifiedAccount("account:" + userId, "TEST", "Test bank", "****1234", "Test owner");
        }
        @Override public Result resolve(Command command) {
            submissions.incrementAndGet();
            return new Result(command.merchantReference(), command.amount(), command.currency(), command.accountToken(),
                    "bank:" + command.merchantReference(), State.SUCCEEDED, null, null);
        }
    }
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired WalletSecurityRepository security;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired InventoryRepository inventories;
    @Autowired WalletService walletService;
    @Autowired OrderPaymentUseCase payments;
    @Autowired SettlementUseCase settlement;
    @Autowired WalletSettlementPolicy policy;
    @Autowired WalletFundingService funding;
    @Autowired WalletBankWorker bankWorker;
    @Autowired BankTransferRepository transfers;
    @Autowired WalletHoldRepository holds;
    @Autowired WalletLedgerService ledger;
    @Autowired WalletJournalRepository journals;
    @Autowired OrderWorkflowUseCase orderService;
    @Autowired WalletJobCursorService cursors;
    @Autowired WalletAlertService alerts;
    @Autowired WalletSettlementRetryService retries;
    @Autowired AdminUserService adminUsers;
    @Autowired com.example.demo.user.service.SessionRevocationService sessions;
    @Autowired ProbeBank bank;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean WalletPinService pins;
    private User buyer;
    private Long walletId;

    @BeforeEach void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        buyer = users.saveAndFlush(User.builder().username("audit_" + suffix).email(suffix + "@example.test")
                .password(encoder.encode("password123")).role(UserRole.CUSTOMER).build());
        walletId = walletService.initializeWallet(buyer.getId()).getId();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            Wallet wallet = wallets.findByIdForUpdate(walletId).orElseThrow();
            wallet.setBalance(new BigDecimal("100"));
            security.saveAndFlush(WalletSecurity.builder().wallet(wallet).pinHash(encoder.encode("123456")).build());
        });
        UserPrincipal principal = new UserPrincipal(buyer);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @AfterEach void clear() { reset(pins); SecurityContextHolder.clearContext(); }

    @Test void paymentRejectsMissingSellerBeforeHoldingMoney() {
        Order order = orderWithoutSeller();
        var exception = assertThrows(com.example.demo.shared.error.AppException.class,
                () -> payments.payOrder(order.getId(), payRequest(), "pay"));
        assertEquals(com.example.demo.shared.error.ErrorCode.WALLET_ORDER_NOT_READY, exception.getErrorCode());
        assertEquals(0, wallets.findById(walletId).orElseThrow().getPendingBalance().signum());
        assertEquals(OrderStatus.PENDING, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test void settlementWorkerContinuesPast1000BlockedRowsOnNextSweep() {
        com.example.demo.ecommerce.api.OrderLifecycleApi repository = mock(com.example.demo.ecommerce.api.OrderLifecycleApi.class);
        SettlementUseCase service = mock(SettlementUseCase.class);
        when(repository.findSettlementDueIds(any(), anyLong(), any())).thenAnswer(invocation -> {
            long cursor = invocation.getArgument(1);
            return LongStream.rangeClosed(cursor + 1, Math.min(cursor + 100, 1001)).boxed().toList();
        });
        when(service.settle(1001L)).thenReturn(true);
        WalletSettlementWorker worker = new WalletSettlementWorker(service, repository, cursors, alerts, retries, true);
        worker.run(); worker.run();
        verify(service, times(1)).settle(1L);
        verify(service, times(1)).settle(1000L);
        verify(service, times(1)).settle(1001L);
    }

    @Test void accountLockAfterPinAuthorizationStopsDebit() {
        Order order = orderWithoutSeller();
        WalletPinService pinTarget = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(pins);
        doAnswer(invocation -> {
            Object authorization = invocation.callRealMethod();
            adminUsers.updateUserStatus(buyer.getId(), false);
            return authorization;
        }).when(pinTarget).authorize(anyString(), anyString());
        var failure = assertThrows(com.example.demo.shared.error.AppException.class,
                () -> payments.payOrder(order.getId(), payRequest(), "pay-after-lock"));
        assertFalse(users.findById(buyer.getId()).orElseThrow().isActive());
        assertEquals(com.example.demo.shared.error.ErrorCode.USER_LOCKED, failure.getErrorCode());
        assertEquals(0, new BigDecimal("100").compareTo(wallets.findById(walletId).orElseThrow().getBalance()));
    }

    @Test void unsubmittedWithdrawalIsSuspendedAfterEmergencyFreeze() {
        LinkBankAccountRequest link = new LinkBankAccountRequest(); link.setVerificationToken("test-proof");
        Long accountId = funding.linkAccount(link).getLinkedBankAccountId();
        WithdrawRequest request = new WithdrawRequest(); request.setPin("123456"); request.setAmount(new BigDecimal("10"));
        request.setLinkedBankAccountId(accountId);
        var result = funding.withdraw(request, "withdraw-before-freeze");
        int submissionsBefore = bank.submissions.get();
        WalletFreezeRequest freeze = new WalletFreezeRequest(); freeze.setReason("Lost account");
        walletService.freezeMyWallet(freeze);
        var transfer = transfers.findByTransactionId(result.getTransactionId()).orElseThrow();
        bankWorker.process(transfer.getId());
        assertEquals(submissionsBefore, bank.submissions.get());
        assertEquals(WalletStatus.LOCKED, wallets.findById(walletId).orElseThrow().getStatus());
        assertEquals(WalletHoldStatus.ACTIVE, holds.findById(result.getHoldId()).orElseThrow().getStatus());
        assertTrue(transfers.findById(transfer.getId()).orElseThrow().isSuspended());
    }
    @Test void revokedSessionAfterPinAuthorizationStopsDebitEvenWhenUserRemainsActive() {
        Order order=orderWithoutSeller();WalletPinService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(pins);
        doAnswer(invocation->{Object auth=invocation.callRealMethod();sessions.revokeAllSessions(buyer.getId());return auth;}).when(target).authorize(anyString(),anyString());
        var error=assertThrows(com.example.demo.shared.error.AppException.class,()->payments.payOrder(order.getId(),payRequest(),"revoked-session"));
        assertEquals(com.example.demo.shared.error.ErrorCode.WALLET_PIN_AUTHORIZATION_EXPIRED,error.getErrorCode());assertEquals(0,new BigDecimal("100").compareTo(wallets.findById(walletId).orElseThrow().getBalance()));
    }

    @Test void ledgerRejectsRoundingBeforePersistingAnyJournal() {
        long before = journals.countUnbalancedJournals();
        assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> ledger.post("audit:" + UUID.randomUUID(), "VND", Map.of(
                "audit:first", new BigDecimal("0.00006"), "audit:second", new BigDecimal("0.00006"),
                "audit:counterpart", new BigDecimal("-0.00012")))));
        assertEquals(before, journals.countUnbalancedJournals());
    }

    private PayOrderRequest payRequest() { var request = new PayOrderRequest(); request.setPin("123456"); return request; }
    private Order orderWithoutSeller() {
        Product product = products.saveAndFlush(Product.builder().name("Audit product " + UUID.randomUUID())
                .price(new BigDecimal("10")).status(ProductStatus.ACTIVE).build());
        inventories.saveAndFlush(Inventory.builder().product(product).availableQuantity(0).lockedQuantity(1).build());
        Order order = Order.builder().userId(buyer.getId()).totalAmount(new BigDecimal("10")).finalAmount(new BigDecimal("10"))
                .status(OrderStatus.PENDING).receiverName("Customer").receiverPhone("0901234567").shippingAddress("Test address").build();
        order.addItem(OrderItem.builder().product(product).productName(product.getName()).quantity(1).priceAtPurchase(product.getPrice()).build());
        policy.snapshot(order);
        return orders.saveAndFlush(order);
    }
}
