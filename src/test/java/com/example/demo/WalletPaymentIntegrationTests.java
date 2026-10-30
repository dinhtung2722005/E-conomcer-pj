package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.ecommerce.entity.*;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.repository.*;
import com.example.demo.ecommerce.service.*;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.*;
import com.example.demo.user.exception.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.wallet.dto.PayOrderRequest;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;

@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
class WalletPaymentIntegrationTests {
    @Autowired WalletPaymentService payments;
    @Autowired WalletService walletService;
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
    @MockitoSpyBean InventoryService inventory;
    private User owner;
    private Long walletId;

    @BeforeEach void prepare() {
        String name = "pay_" + UUID.randomUUID().toString().replace("-", "");
        owner = users.saveAndFlush(User.builder().username(name).email(name + "@example.test")
                .password(encoder.encode("password123")).role(UserRole.CUSTOMER).build());
        authenticate();
        walletId = walletService.initializeMyWallet().getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var wallet = wallets.findByIdForUpdate(walletId).orElseThrow();
            wallet.setBalance(new BigDecimal("100")); wallet.setBonusBalance(new BigDecimal("40"));
            security.saveAndFlush(WalletSecurity.builder().wallet(wallet).pinHash(encoder.encode("123456")).build());
        });
    }
    @AfterEach void clear() { reset(inventory); SecurityContextHolder.clearContext(); }

    @Test void paymentReservesCashAndBonusAndCancellationRestoresEachSourceOnce() {
        var order = order("90");
        var response = payments.payOrder(order.getId(), request(true), "pay");
        money("50", response.getCashAmount()); money("40", response.getBonusAmount());
        money("50", wallet().getBalance()); money("50", wallet().getPendingBalance());
        money("0", wallet().getBonusBalance()); money("40", wallet().getPendingBonusBalance());
        assertEquals(response.getTransactionIds(), payments.payOrder(order.getId(), request(true), "pay").getTransactionIds());
        orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
        orderService.cancelOrder(order.getId());
        money("100", wallet().getBalance()); money("40", wallet().getBonusBalance());
        money("0", wallet().getPendingBalance()); money("0", wallet().getPendingBonusBalance());
        var history = transactions.findByWalletIdAndReferenceIdOrderByIdAsc(walletId, order.getId().toString());
        assertEquals(2, history.stream().filter(value -> value.getType() == TransactionType.REFUND).count());
    }

    @Test void insufficientBalanceAndWrongPinDoNotChangeOrderOrMoney() {
        var order = order("141");
        error(ErrorCode.WALLET_INSUFFICIENT_BALANCE, () -> payments.payOrder(order.getId(), request(true), "pay"));
        var invalid = request(true); invalid.setPin("000000");
        error(ErrorCode.WALLET_PIN_INCORRECT, () -> payments.payOrder(order.getId(), invalid, "pay"));
        assertEquals(1, security.findById(walletId).orElseThrow().getFailedPinAttempts());
        assertEquals(OrderStatus.PENDING, orders.findById(order.getId()).orElseThrow().getStatus());
        money("100", wallet().getBalance()); money("0", wallet().getPendingBalance());
    }

    @Test void stockFailureRollsBackWalletHoldTransactionsAndOrder() {
        var order = order("90");
        Long productId = new TransactionTemplate(transactionManager).execute(status ->
                orders.findById(order.getId()).orElseThrow().getItems().getFirst().getProduct().getId());
        doThrow(new AppException(ErrorCode.INVENTORY_INCONSISTENT)).when(inventory).confirmSale(productId, 1);
        error(ErrorCode.INVENTORY_INCONSISTENT, () -> payments.payOrder(order.getId(), request(true), "pay"));
        money("100", wallet().getBalance()); money("40", wallet().getBonusBalance());
        money("0", wallet().getPendingBalance()); money("0", wallet().getPendingBonusBalance());
        assertTrue(holds.findByWalletIdAndOrderIdOrderByIdAsc(walletId, order.getId()).isEmpty());
        assertTrue(transactions.findByWalletIdAndReferenceIdOrderByIdAsc(walletId, order.getId().toString()).isEmpty());
        assertEquals(OrderStatus.PENDING, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test void refundFailureRollsBackOrderAndBalancesTogether() {
        var order = order("90");
        payments.payOrder(order.getId(), request(true), "pay");
        Long productId = new TransactionTemplate(transactionManager).execute(status ->
                orders.findById(order.getId()).orElseThrow().getItems().getFirst().getProduct().getId());
        doThrow(new AppException(ErrorCode.INVENTORY_INCONSISTENT)).when(inventory).restock(productId, 1);
        error(ErrorCode.INVENTORY_INCONSISTENT, () -> orderService.cancelOrder(order.getId()));
        money("50", wallet().getBalance()); money("50", wallet().getPendingBalance());
        money("40", wallet().getPendingBonusBalance());
        assertEquals(OrderStatus.PAID, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(0, transactions.findByWalletIdAndReferenceIdOrderByIdAsc(walletId, order.getId().toString())
                .stream().filter(value -> value.getType() == TransactionType.REFUND).count());
    }

    @Test void keyCannotChangeBonusPolicyOrOrderAndAdminCannotBypassWallet() {
        var first = order("50"); var second = order("20");
        error(ErrorCode.ORDER_STATUS_INVALID, () -> orderService.updateOrderStatus(first.getId(), OrderStatus.PAID));
        payments.payOrder(first.getId(), request(false), "pay");
        error(ErrorCode.WALLET_IDEMPOTENCY_CONFLICT, () -> payments.payOrder(first.getId(), request(true), "pay"));
        error(ErrorCode.WALLET_IDEMPOTENCY_CONFLICT, () -> payments.payOrder(second.getId(), request(false), "pay"));
        error(ErrorCode.ORDER_STATUS_INVALID, () -> payments.payOrder(first.getId(), request(false), "another-key"));
    }

    @Test void zeroTotalCreatesNoZeroAmountHoldsOrTransactions() {
        var order = order("0");
        var result = payments.payOrder(order.getId(), request(false), "free");
        assertTrue(result.getHoldIds().isEmpty()); assertTrue(result.getTransactionIds().isEmpty());
        assertEquals(OrderStatus.PAID, orders.findById(order.getId()).orElseThrow().getStatus());
        orderService.cancelOrder(order.getId());
        money("100", wallet().getBalance());
    }

    @Test void twoConcurrentOrdersCannotSpendTheSameCash() throws Exception {
        var firstOrder = order("80"); var secondOrder = order("80");
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> attempt(firstOrder.getId(), start));
            var second = executor.submit(() -> attempt(secondOrder.getId(), start)); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        money("20", wallet().getBalance()); money("80", wallet().getPendingBalance());
    }

    private boolean attempt(Long orderId, CountDownLatch start) throws Exception {
        authenticate(); start.await();
        try { payments.payOrder(orderId, request(false), "pay:" + orderId); return true; }
        catch (AppException exception) { assertEquals(ErrorCode.WALLET_INSUFFICIENT_BALANCE, exception.getErrorCode()); return false; }
        finally { SecurityContextHolder.clearContext(); }
    }
    private Order order(String amount) {
        var product = products.saveAndFlush(Product.builder().name("Product " + UUID.randomUUID())
                .price(new BigDecimal(amount)).status(ProductStatus.ACTIVE).build());
        inventories.saveAndFlush(Inventory.builder().product(product).availableQuantity(0).lockedQuantity(1).build());
        var order = Order.builder().userId(owner.getId()).totalAmount(new BigDecimal(amount)).finalAmount(new BigDecimal(amount))
                .status(OrderStatus.PENDING).receiverName("Customer").receiverPhone("0901234567").shippingAddress("Test address").build();
        order.addItem(OrderItem.builder().product(product).productName(product.getName()).quantity(1)
                .priceAtPurchase(product.getPrice()).build());
        return orders.saveAndFlush(order);
    }
    private void authenticate() {
        var principal = new UserPrincipal(owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private PayOrderRequest request(boolean bonus) { var request = new PayOrderRequest(); request.setPin("123456"); request.setUseBonus(bonus); return request; }
    private Wallet wallet() { return wallets.findById(walletId).orElseThrow(); }
    private void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) { assertEquals(code, assertThrows(AppException.class, action).getErrorCode()); }
}
