package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.ecommerce.entity.*;
import com.example.demo.ecommerce.repository.*;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;

@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN",
        "wallet.settlement.fee-percent=10", "wallet.cashback.percent=5"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
class WalletSettlementIntegrationTests {
    @Autowired WalletSettlementService settlement;
    @Autowired WalletSettlementPolicy policy;
    @Autowired WalletPaymentService payments;
    @Autowired WalletRefundService refunds;
    @Autowired WalletService walletService;
    @Autowired WalletLedgerService ledger;
    @Autowired WalletReconciliationService reconciliation;
    @Autowired OrderService orderService;
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired WalletSecurityRepository security;
    @Autowired WalletTransactionRepository transactions;
    @Autowired WalletHoldRepository holds;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired InventoryRepository inventories;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean WalletJournalRepository journals;
    private User buyer, firstSeller, secondSeller, admin;
    private Long buyerWalletId, firstWalletId, secondWalletId;
    private BigDecimal feesBefore;

    @BeforeEach void prepare() {
        buyer = user(UserRole.CUSTOMER); firstSeller = user(UserRole.SELLER); secondSeller = user(UserRole.SELLER); admin = user(UserRole.ADMIN);
        buyerWalletId = walletService.initializeWallet(buyer.getId()).getId();
        firstWalletId = walletService.initializeWallet(firstSeller.getId()).getId();
        secondWalletId = walletService.initializeWallet(secondSeller.getId()).getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var wallet = wallets.findByIdForUpdate(buyerWalletId).orElseThrow();
            wallet.setBalance(new BigDecimal("100")); wallet.setBonusBalance(new BigDecimal("40"));
            security.saveAndFlush(WalletSecurity.builder().wallet(wallet).pinHash(encoder.encode("123456")).build());
        });
        authenticate(buyer);
        feesBefore = journals.accountBalance("platform:FEES", "VND");
    }
    @AfterEach void clear() { reset(journals); SecurityContextHolder.clearContext(); }

    @Test void settlementCreditsSnapshotSellersFeesAndBonusOnceWithBalancedJournals() {
        var order = paidOrder(); complete(order.getId());
        assertFalse(settlement.settle(order.getId()));
        due(order.getId());
        assertTrue(settlement.settle(order.getId())); assertFalse(settlement.settle(order.getId()));
        money("27", wallet(firstWalletId).getBalance()); money("54", wallet(secondWalletId).getBalance());
        money("50", wallet(buyerWalletId).getBalance()); money("0", wallet(buyerWalletId).getPendingBalance());
        money("0", wallet(buyerWalletId).getPendingBonusBalance()); money("4.5", wallet(buyerWalletId).getBonusBalance());
        assertNotNull(orders.findById(order.getId()).orElseThrow().getSettledAt());
        assertEquals(0, journals.countUnbalancedJournals());
        assertLedgerMatches(wallet(buyerWalletId)); assertLedgerMatches(wallet(firstWalletId)); assertLedgerMatches(wallet(secondWalletId));
        assertEquals(0, feesBefore.add(new BigDecimal("9")).compareTo(journals.accountBalance("platform:FEES", "VND")));
    }

    @Test void requestedReturnBlocksSettlementUntilApprovedAndRestoresOriginalSources() {
        var order = paidOrder(); complete(order.getId());
        var request = new RefundCaseRequest(); request.setReason("Damaged item");
        var response = refunds.requestRefund(order.getId(), request);
        assertEquals(response.getId(), refunds.requestRefund(order.getId(), request).getId());
        due(order.getId()); assertFalse(settlement.settle(order.getId()));
        authenticate(admin);
        var decision = decision(true);
        refunds.decide(order.getId(), decision); refunds.decide(order.getId(), decision);
        assertFalse(settlement.settle(order.getId()));
        money("100", wallet(buyerWalletId).getBalance()); money("40", wallet(buyerWalletId).getBonusBalance());
        money("0", wallet(firstWalletId).getBalance()); money("0", wallet(secondWalletId).getBalance());
        assertEquals(OrderStatus.REFUNDED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertLedgerMatches(wallet(buyerWalletId));
    }

    @Test void rejectedReturnCanSettleAndOppositeReviewCannotOverwriteDecision() {
        var order = paidOrder(); complete(order.getId());
        var request = new RefundCaseRequest(); request.setReason("Return request"); refunds.requestRefund(order.getId(), request);
        authenticate(admin); refunds.decide(order.getId(), decision(false));
        assertEquals(ErrorCode.DATA_CONFLICT, assertThrows(AppException.class,
                () -> refunds.decide(order.getId(), decision(true))).getErrorCode());
        due(order.getId()); assertTrue(settlement.settle(order.getId()));
    }

    @Test void expiredReturnWindowCannotBeReopenedByCustomer() {
        var order = paidOrder(); complete(order.getId()); due(order.getId());
        var request = new RefundCaseRequest(); request.setReason("Too late");
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, assertThrows(AppException.class,
                () -> refunds.requestRefund(order.getId(), request)).getErrorCode());
    }

    @Test void journalFailureRollsBackCaptureSellerCreditRewardAndOrder() {
        var order = paidOrder(); complete(order.getId()); due(order.getId());
        doThrow(new DataIntegrityViolationException("Simulated journal outage")).when(journals).saveAndFlush(any(WalletJournal.class));
        assertThrows(DataIntegrityViolationException.class, () -> settlement.settle(order.getId()));
        money("50", wallet(buyerWalletId).getPendingBalance()); money("40", wallet(buyerWalletId).getPendingBonusBalance());
        money("0", wallet(firstWalletId).getBalance()); money("0", wallet(secondWalletId).getBalance());
        assertNull(orders.findById(order.getId()).orElseThrow().getSettledAt());
        assertTrue(holds.findByWalletIdAndOrderIdOrderByIdAsc(buyerWalletId, order.getId()).stream()
                .allMatch(hold -> hold.getStatus() == WalletHoldStatus.ACTIVE));
    }

    @Test void concurrentSettlementRunsCannotPaySellerTwice() throws Exception {
        var order = paidOrder(); complete(order.getId()); due(order.getId());
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> action = () -> { start.await(); return settlement.settle(order.getId()); };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        money("27", wallet(firstWalletId).getBalance()); money("54", wallet(secondWalletId).getBalance());
    }

    @Test void changingProductSellerDoesNotChangeExistingOrderRecipient() {
        var order = paidOrder();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var loaded = orders.findById(order.getId()).orElseThrow();
            loaded.getItems().forEach(item -> products.findById(item.getProduct().getId()).orElseThrow().setSellerId(buyer.getId()));
        });
        complete(order.getId()); due(order.getId()); assertTrue(settlement.settle(order.getId()));
        money("27", wallet(firstWalletId).getBalance()); money("54", wallet(secondWalletId).getBalance());
    }

    @Test void reconciliationDetectsBalanceAndHeldMoneyDriftWithoutRepairingIt() {
        paidOrder();
        var healthy = reconciliation.inspect(buyerWalletId);
        assertEquals(Boolean.TRUE, healthy.getLedgerConsistent()); assertTrue(healthy.isHoldsConsistent());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var wallet = wallets.findByIdForUpdate(buyerWalletId).orElseThrow();
            wallet.setBalance(wallet.getBalance().add(BigDecimal.ONE));
            wallet.setPendingBalance(wallet.getPendingBalance().add(BigDecimal.ONE));
        });
        var report = reconciliation.inspect(buyerWalletId);
        assertEquals(Boolean.FALSE, report.getLedgerConsistent()); assertFalse(report.isHoldsConsistent());
        money("1", report.getLedgerDifferences().get("CASH_AVAILABLE")); money("1", report.getCashHeldDifference());
        money("51", wallet(buyerWalletId).getBalance());
    }

    @Test void uninitializedLedgerIsNotReportedAsMatchingAndAdminInspectionIsRestricted() {
        var report = reconciliation.inspect(firstWalletId);
        assertFalse(report.isLedgerInitialized()); assertNull(report.getLedgerConsistent());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> reconciliation.inspectAsAdmin(firstWalletId));
        authenticate(admin); assertNotNull(reconciliation.inspectAsAdmin(firstWalletId));
    }

    private Order paidOrder() {
        var order = Order.builder().userId(buyer.getId()).totalAmount(new BigDecimal("90")).finalAmount(new BigDecimal("90"))
                .status(OrderStatus.PENDING).receiverName("Customer").receiverPhone("0901234567").shippingAddress("Test address").build();
        add(order, firstSeller, "30"); add(order, secondSeller, "60"); policy.snapshot(order);
        order = orders.saveAndFlush(order);
        var request = new PayOrderRequest(); request.setPin("123456"); request.setUseBonus(true);
        payments.payOrder(order.getId(), request, "pay"); return order;
    }
    private void add(Order order, User seller, String amount) {
        var product = products.saveAndFlush(Product.builder().name("Product " + UUID.randomUUID()).sellerId(seller.getId())
                .price(new BigDecimal(amount)).status(ProductStatus.ACTIVE).build());
        inventories.saveAndFlush(Inventory.builder().product(product).availableQuantity(0).lockedQuantity(1).build());
        order.addItem(OrderItem.builder().product(product).productName(product.getName()).sellerId(seller.getId())
                .quantity(1).priceAtPurchase(product.getPrice()).build());
    }
    private void complete(Long orderId) { orderService.updateOrderStatus(orderId, OrderStatus.SHIPPED); orderService.confirmReceipt(orderId); }
    private void due(Long orderId) { new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            orders.findByIdForUpdate(orderId).orElseThrow().setSettlementAvailableAt(LocalDateTime.now().minusSeconds(1))); }
    private RefundDecisionRequest decision(boolean approved) { var request = new RefundDecisionRequest(); request.setApproved(approved); request.setReason("Reviewed evidence"); return request; }
    private User user(UserRole role) { String name = "settle_" + UUID.randomUUID().toString().replace("-", ""); return users.saveAndFlush(User.builder()
            .username(name).email(name + "@example.test").password(encoder.encode("password123")).role(role).build()); }
    private void authenticate(User user) { var principal = new UserPrincipal(user); SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())); }
    private Wallet wallet(Long id) { return wallets.findById(id).orElseThrow(); }
    private void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private void assertLedgerMatches(Wallet wallet) {
        assertEquals(0, wallet.getBalance().compareTo(journals.accountBalance(ledger.account(wallet, WalletBalanceType.CASH, false), "VND")));
        assertEquals(0, wallet.getPendingBalance().compareTo(journals.accountBalance(ledger.account(wallet, WalletBalanceType.CASH, true), "VND")));
        assertEquals(0, wallet.getBonusBalance().compareTo(journals.accountBalance(ledger.account(wallet, WalletBalanceType.BONUS, false), "VND")));
        assertEquals(0, wallet.getPendingBonusBalance().compareTo(journals.accountBalance(ledger.account(wallet, WalletBalanceType.BONUS, true), "VND")));
    }
}
