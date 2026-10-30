package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import com.example.demo.ecommerce.dto.OrderRequest;
import com.example.demo.ecommerce.dto.OrderResponse;
import com.example.demo.ecommerce.entity.*;
import com.example.demo.ecommerce.repository.*;
import com.example.demo.ecommerce.service.CheckoutService;
import com.example.demo.ecommerce.service.OrderService;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.dto_request.ChangePasswordRequest;
import com.example.demo.user.dto_request.LoginRequest;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.RefreshTokenRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.JwtUtils;
import com.example.demo.user.service.AdminUserService;
import com.example.demo.user.service.AuthService;
import com.example.demo.user.service.UserService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
class CriticalFlowsIntegrationTests {
    private static final String PASSWORD = "Integration-password-123";

    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    @Autowired InventoryRepository inventories;
    @Autowired CartRepository carts;
    @Autowired OrderRepository orders;
    @Autowired VoucherRepository vouchers;
    @Autowired UserVoucherRepository userVouchers;
    @Autowired FlashSaleRepository flashSales;
    @Autowired FlashSaleItemRepository flashSaleItems;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CheckoutService checkout;
    @Autowired OrderService orderService;
    @Autowired com.example.demo.wallet.service.WalletPaymentService walletPayments;
    @Autowired com.example.demo.wallet.service.WalletService walletService;
    @Autowired com.example.demo.wallet.repository.WalletRepository wallets;
    @Autowired com.example.demo.wallet.repository.WalletSecurityRepository walletSecurity;
    @Autowired AuthService auth;
    @Autowired UserService userService;
    @Autowired AdminUserService adminUsers;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoSpyBean JwtUtils jwtUtils;
    @Value("${local.server.port}") int port;

    @Test
    void concurrentCheckoutsCannotSellTheLastUnitTwice() throws Exception {
        Product product = product(1);
        User first = user();
        User second = user();
        cart(first, product);
        cart(second, product);

        var results = concurrently(
                () -> checkout.placeOrder(first.getId(), request(null)),
                () -> checkout.placeOrder(second.getId(), request(null)));

        assertEquals(1, results.stream().filter(Result::success).count());
        assertEquals(1, results.stream().filter(result -> result.error() == ErrorCode.OUT_OF_STOCK).count());
        assertStock(product, 0, 1);
    }

    @Test
    void concurrentCheckoutOfTheSameCartCreatesOnlyOneOrder() throws Exception {
        Product product = product(5);
        User customer = user();
        cart(customer, product);

        var results = concurrently(
                () -> checkout.placeOrder(customer.getId(), request(null)),
                () -> checkout.placeOrder(customer.getId(), request(null)));

        assertEquals(1, results.stream().filter(Result::success).count());
        assertEquals(1, results.stream().filter(result -> result.error() == ErrorCode.CART_EMPTY).count());
        assertEquals(1, orders.findByUserId(customer.getId()).size());
        assertStock(product, 4, 1);
    }

    @Test
    void oppositeProductOrderInCartsDoesNotDeadlock() throws Exception {
        Product firstProduct = product(2);
        Product secondProduct = product(2);
        User first = user();
        User second = user();
        cart(first, firstProduct, secondProduct);
        cart(second, secondProduct, firstProduct);

        var results = concurrently(
                () -> checkout.placeOrder(first.getId(), request(null)),
                () -> checkout.placeOrder(second.getId(), request(null)));

        assertTrue(results.stream().allMatch(Result::success));
        assertStock(firstProduct, 0, 2);
        assertStock(secondProduct, 0, 2);
    }

    @Test
    void cancellingAPaidOrderConcurrentlyRestocksAndRefundsOnlyOnce() throws Exception {
        Product product = product(1);
        User customer = user();
        Voucher voucher = voucher(DiscountType.FIXED_AMOUNT, "10", null, 1);
        saveVoucher(customer, voucher);
        cart(customer, product);
        Long orderId = checkout.placeOrder(customer.getId(), request(voucher.getCode())).getId();
        fundWallet(customer);
        pay(customer, orderId);

        var results = concurrently(() -> orderService.cancelOrder(orderId), () -> orderService.cancelOrder(orderId));

        assertTrue(results.stream().allMatch(Result::success));
        assertEquals(OrderStatus.CANCELLED, orders.findById(orderId).orElseThrow().getStatus());
        assertStock(product, 1, 0);
        assertEquals(0, vouchers.findById(voucher.getId()).orElseThrow().getUsedQuantity());
        assertFalse(userVouchers.findByUserIdAndVoucherId(customer.getId(), voucher.getId()).orElseThrow().getIsUsed());
    }

    @Test
    void paymentRacingCancellationCannotConsumeOrRestoreStockTwice() throws Exception {
        Product product = product(1);
        User customer = user();
        cart(customer, product);
        Long orderId = checkout.placeOrder(customer.getId(), request(null)).getId();

        fundWallet(customer);
        var results = concurrently(
                () -> pay(customer, orderId),
                () -> orderService.cancelOrder(orderId));

        assertTrue(results.get(1).success());
        assertTrue(results.get(0).success() || results.get(0).error() == ErrorCode.ORDER_STATUS_INVALID);
        assertEquals(OrderStatus.CANCELLED, orders.findById(orderId).orElseThrow().getStatus());
        assertStock(product, 1, 0);
    }

    @Test
    void expiryCleanupDoesNotCancelAnOrderThatHasBeenPaid() {
        Product product = product(1);
        User customer = user();
        cart(customer, product);
        Long orderId = checkout.placeOrder(customer.getId(), request(null)).getId();
        fundWallet(customer);
        pay(customer, orderId);

        assertFalse(orderService.cancelExpiredOrder(orderId, LocalDateTime.now().plusMinutes(1)));
        assertEquals(OrderStatus.PAID, orders.findById(orderId).orElseThrow().getStatus());
        assertStock(product, 0, 0);
    }

    @Test
    void expiryCleanupReleasesPendingOrdersOnlyOnce() {
        Product product = product(1);
        User customer = user();
        cart(customer, product);
        Long orderId = checkout.placeOrder(customer.getId(), request(null)).getId();
        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(1);

        assertTrue(orderService.cancelExpiredOrder(orderId, cutoff));
        assertFalse(orderService.cancelExpiredOrder(orderId, cutoff));
        assertStock(product, 1, 0);
    }

    @Test
    void twoOrdersCannotConsumeTheLastVoucherUse() throws Exception {
        User first = user();
        User second = user();
        Product firstProduct = product(1);
        Product secondProduct = product(1);
        Voucher voucher = voucher(DiscountType.FIXED_AMOUNT, "10", null, 1);
        saveVoucher(first, voucher);
        saveVoucher(second, voucher);
        cart(first, firstProduct);
        cart(second, secondProduct);

        var results = concurrently(
                () -> checkout.placeOrder(first.getId(), request(voucher.getCode())),
                () -> checkout.placeOrder(second.getId(), request(voucher.getCode())));

        assertEquals(1, results.stream().filter(Result::success).count());
        assertEquals(1, results.stream().filter(result -> result.error() == ErrorCode.VOUCHER_USAGE_LIMIT).count());
        assertEquals(1, vouchers.findById(voucher.getId()).orElseThrow().getUsedQuantity());
        int totalHeld = inventories.findByProductId(firstProduct.getId()).orElseThrow().getLockedQuantity()
                + inventories.findByProductId(secondProduct.getId()).orElseThrow().getLockedQuantity();
        assertEquals(1, totalHeld);
    }

    @Test
    void flashSaleQuotaIsNotExceededByConcurrentOrders() throws Exception {
        Product product = product(2);
        FlashSaleItem promotion = promotion(product, 1);
        User first = user();
        User second = user();
        cart(first, product);
        cart(second, product);

        var results = concurrently(
                () -> checkout.placeOrder(first.getId(), request(null)),
                () -> checkout.placeOrder(second.getId(), request(null)));

        assertTrue(results.stream().allMatch(Result::success));
        var amounts = results.stream().map(result -> result.value().getFinalAmount().stripTrailingZeros()).toList();
        assertTrue(amounts.contains(new BigDecimal("30").stripTrailingZeros()));
        assertTrue(amounts.contains(new BigDecimal("100").stripTrailingZeros()));
        assertEquals(1, flashSaleItems.findById(promotion.getId()).orElseThrow().getSoldQuantity());
        assertStock(product, 0, 2);
    }

    @Test
    void failedCheckoutRollsBackStockPromotionAndCartChanges() {
        Product product = product(1);
        FlashSaleItem promotion = promotion(product, 1);
        User customer = user();
        Voucher voucher = voucher(DiscountType.FIXED_AMOUNT, "10", null, 1);
        cart(customer, product); // Không lưu voucher vào ví.

        AppException failure = assertThrows(AppException.class,
                () -> checkout.placeOrder(customer.getId(), request(voucher.getCode())));

        assertEquals(ErrorCode.VOUCHER_NOT_SAVED, failure.getErrorCode());
        assertStock(product, 1, 0);
        assertEquals(0, flashSaleItems.findById(promotion.getId()).orElseThrow().getSoldQuantity());
        assertEquals(0, vouchers.findById(voucher.getId()).orElseThrow().getUsedQuantity());
        assertTrue(orders.findByUserId(customer.getId()).isEmpty());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id WHERE c.user_id = ?",
                Integer.class, customer.getId()));
    }

    @Test
    void percentageDiscountRespectsItsCap() {
        assertDiscount(DiscountType.PERCENTAGE, "50", "30", "30", "70");
    }

    @Test
    void fixedDiscountIsNotInterpretedAsAPercentage() {
        assertDiscount(DiscountType.FIXED_AMOUNT, "25", null, "25", "75");
    }

    @Test
    void discountNeverMakesTheFinalAmountNegative() {
        assertDiscount(DiscountType.FIXED_AMOUNT, "500", null, "100", "0");
    }

    @Test
    void theSameRefreshTokenCanOnlyBeConsumedOnceConcurrently() throws Exception {
        User customer = user();
        AuthService.Session original = login(customer);

        var results = concurrently(() -> auth.refresh(original.refreshToken()), () -> auth.refresh(original.refreshToken()));

        assertEquals(1, results.stream().filter(Result::success).count());
        assertEquals(1, results.stream().filter(result -> result.error() == ErrorCode.REFRESH_TOKEN_NOT_FOUND).count());
        AuthService.Session rotated = results.stream().filter(Result::success).findFirst().orElseThrow().value();
        assertNotEquals(original.refreshToken(), rotated.refreshToken());
        assertTrue(refreshTokens.findByToken(original.refreshToken()).isEmpty());
        assertNotNull(auth.refresh(rotated.refreshToken()));
    }

    @Test
    void failureToSignAnAccessTokenRollsBackRefreshRotation() {
        User customer = user();
        AuthService.Session original = login(customer);
        try {
            doThrow(new IllegalStateException("Simulated signing failure")).when(jwtUtils).generateToken(any(User.class));
            assertThrows(IllegalStateException.class, () -> auth.refresh(original.refreshToken()));
        } finally {
            reset(jwtUtils);
        }
        assertTrue(refreshTokens.findByToken(original.refreshToken()).isPresent());
        assertNotNull(auth.refresh(original.refreshToken()));
    }

    @Test
    void passwordChangeRevokesEveryAccessTokenAndTheRefreshToken() throws Exception {
        User customer = user();
        AuthService.Session first = login(customer);
        AuthService.Session second = login(customer);
        assertEquals(200, http("GET", "/api/details/profile", first.accessToken()).statusCode());

        userService.changePassword(customer.getUsername(), new ChangePasswordRequest(PASSWORD, "Changed-password-456", "Changed-password-456"));

        assertEquals(401, http("GET", "/api/details/profile", first.accessToken()).statusCode());
        assertEquals(401, http("GET", "/api/details/profile", second.accessToken()).statusCode());
        assertThrows(AppException.class, () -> auth.refresh(second.refreshToken()));
        AuthService.Session current = auth.login(new LoginRequest(customer.getUsername(), "Changed-password-456"));
        assertEquals(200, http("GET", "/api/details/profile", current.accessToken()).statusCode());
        auth.logout(first.accessToken());
        assertNotNull(auth.refresh(current.refreshToken()), "Old-session logout must not revoke a newer session");
    }

    @Test
    void failedPasswordChangeLeavesTheExistingSessionUsable() throws Exception {
        User customer = user();
        AuthService.Session session = login(customer);
        assertThrows(AppException.class, () -> userService.changePassword(customer.getUsername(),
                new ChangePasswordRequest("wrong-password", "Changed-password-456", "Changed-password-456")));
        assertEquals(200, http("GET", "/api/details/profile", session.accessToken()).statusCode());
        assertNotNull(auth.refresh(session.refreshToken()));
    }

    @Test
    void unlockingAnAccountDoesNotReactivateItsOldSessions() throws Exception {
        User customer = user();
        AuthService.Session session = login(customer);
        adminUsers.updateUserStatus(customer.getId(), false);
        assertEquals(401, http("GET", "/api/details/profile", session.accessToken()).statusCode());
        adminUsers.updateUserStatus(customer.getId(), true);
        assertEquals(401, http("GET", "/api/details/profile", session.accessToken()).statusCode());
        assertThrows(AppException.class, () -> auth.refresh(session.refreshToken()));
        assertEquals(200, http("GET", "/api/details/profile", login(customer).accessToken()).statusCode());
    }

    @Test
    void refreshRacingPasswordChangeCannotLeaveAValidOldSession() throws Exception {
        User customer = user();
        AuthService.Session session = login(customer);
        var results = concurrently(() -> auth.refresh(session.refreshToken()), () -> {
            userService.changePassword(customer.getUsername(),
                    new ChangePasswordRequest(PASSWORD, "Changed-password-456", "Changed-password-456"));
            return session;
        });
        assertTrue(results.get(1).success());
        assertEquals(401, http("GET", "/api/details/profile", session.accessToken()).statusCode());
        if (results.get(0).success()) {
            assertEquals(401, http("GET", "/api/details/profile", results.get(0).value().accessToken()).statusCode());
        } else {
            assertEquals(ErrorCode.REFRESH_TOKEN_NOT_FOUND, results.get(0).error());
        }
    }

    @Test
    void aCustomerCannotReadOrCancelAnotherCustomersOrder() throws Exception {
        Product product = product(1);
        User owner = user();
        User stranger = user();
        cart(owner, product);
        Long orderId = checkout.placeOrder(owner.getId(), request(null)).getId();
        AuthService.Session session = login(stranger);

        assertEquals(404, http("GET", "/api/orders/" + orderId, session.accessToken()).statusCode());
        assertEquals(404, http("POST", "/api/orders/" + orderId + "/cancel", session.accessToken()).statusCode());
        assertEquals(403, http("GET", "/api/admin/categories", session.accessToken()).statusCode());
        assertEquals(OrderStatus.PENDING, orders.findById(orderId).orElseThrow().getStatus());
        assertStock(product, 0, 1);
    }

    @Test
    void updatingAStaleProfileCannotRestoreAnOldPasswordOrTokenVersion() throws Exception {
        User customer = user();
        AuthService.Session session = login(customer);
        CountDownLatch profileLoaded = new CountDownLatch(1);
        CountDownLatch passwordChanged = new CountDownLatch(1);

        var results = concurrently(() -> new TransactionTemplate(transactions).execute(status -> {
            User staleProfile = users.findById(customer.getId()).orElseThrow();
            profileLoaded.countDown();
            try {
                if (!passwordChanged.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Password change did not finish");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            staleProfile.setEmail("changed_" + customer.getEmail());
            users.saveAndFlush(staleProfile);
            return true;
        }), () -> {
            try {
                assertTrue(profileLoaded.await(5, TimeUnit.SECONDS));
                userService.changePassword(customer.getUsername(),
                        new ChangePasswordRequest(PASSWORD, "Changed-password-456", "Changed-password-456"));
                return true;
            } finally {
                passwordChanged.countDown();
            }
        });

        assertTrue(results.stream().allMatch(Result::success));
        User updated = users.findById(customer.getId()).orElseThrow();
        assertEquals("changed_" + customer.getEmail(), updated.getEmail());
        assertTrue(passwordEncoder.matches("Changed-password-456", updated.getPassword()));
        assertEquals(1L, updated.getTokenVersion());
        assertEquals(401, http("GET", "/api/details/profile", session.accessToken()).statusCode());
    }

    @Test
    void adminLoginRefreshAndLogoutKeepTheHttpApiContract() throws Exception {
        User admin = user();
        admin.setRole(UserRole.ADMIN);
        users.saveAndFlush(admin);
        var loginRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(new LoginRequest(admin.getUsername(), PASSWORD))))
                .build();
        var client = HttpClient.newHttpClient();
        var loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResponse.statusCode());
        var loginData = json.readTree(loginResponse.body()).get("result");
        assertEquals("ADMIN", loginData.get("user").get("role").asString());
        assertTrue(loginData.get("user").get("isActive").asBoolean());
        assertFalse(loginData.get("user").has("active"));
        assertFalse(loginData.get("user").has("password"));
        String accessToken = loginData.get("accessToken").asString();
        String refreshCookie = loginResponse.headers().allValues("set-cookie").stream()
                .filter(cookie -> cookie.startsWith("refresh_jwt=")).findFirst().orElseThrow();
        String refreshToken = refreshCookie.substring("refresh_jwt=".length(), refreshCookie.indexOf(';'));
        assertTrue(refreshCookie.contains("HttpOnly"));

        var refreshRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/refresh"))
                .timeout(Duration.ofSeconds(10)).header("Refresh-Token", refreshToken)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        var refreshResponse = client.send(refreshRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, refreshResponse.statusCode());
        String rotatedAccessToken = json.readTree(refreshResponse.body()).get("result").get("accessToken").asString();
        assertNotEquals(accessToken, rotatedAccessToken);
        assertEquals(401, client.send(refreshRequest, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, http("GET", "/api/admin/categories", rotatedAccessToken).statusCode());
        assertEquals(200, http("POST", "/api/auth/logout", rotatedAccessToken).statusCode());
        assertEquals(401, http("GET", "/api/admin/categories", rotatedAccessToken).statusCode());
    }

    @Test
    void adminListsShareValidatedPagingAndStableOrder() throws Exception {
        String token = adminToken();
        FlashSale sale = null;
        for (int i = 0; i < 2; i++) {
            Product product = product(5);
            User customer = user();
            cart(customer, product);
            checkout.placeOrder(customer.getId(), request(null));
            voucher(DiscountType.FIXED_AMOUNT, "5", null, 10);
            categories.saveAndFlush(Category.builder().name("Paging " + UUID.randomUUID()).build());
            FlashSaleItem promotion = promotion(product, 2);
            if (sale == null) {
                sale = promotion.getFlashSale();
            } else {
                flashSaleItems.saveAndFlush(FlashSaleItem.builder().flashSale(sale).product(product)
                        .promotionalPrice(new BigDecimal("20")).totalQuantity(2).build());
            }
        }
        List<String> paths = List.of("/api/admin/users", "/api/users", "/api/admin/categories",
                "/api/admin/products", "/api/admin/inventories", "/api/admin/vouchers", "/api/admin/orders",
                "/api/admin/flash-sales", "/api/admin/flash-sales/active",
                "/api/admin/flash-sales/" + sale.getId() + "/items");
        for (String path : paths) {
            var firstResponse = http("GET", path + "?page=1&size=1", token);
            assertEquals(200, firstResponse.statusCode(), path + ": " + firstResponse.body());
            var first = json.readTree(firstResponse.body()).get("result");
            var secondResponse = http("GET", path + "?page=2&size=1", token);
            assertEquals(200, secondResponse.statusCode(), path);
            var second = json.readTree(secondResponse.body()).get("result");
            assertEquals(1, first.get("currentPage").asInt(), path);
            assertEquals(2, second.get("currentPage").asInt(), path);
            assertEquals(1, first.get("data").size(), path);
            assertEquals(1, second.get("data").size(), path);
            assertTrue(first.get("data").get(0).get("id").asLong()
                    > second.get("data").get(0).get("id").asLong(), path);
            assertEquals(first.get("totalElements"), second.get("totalElements"), path);
            assertTrue(first.get("totalPages").asInt() >= 2, path);
            var defaults = json.readTree(http("GET", path, token).body()).get("result");
            assertEquals(1, defaults.get("currentPage").asInt(), path);
            assertTrue(defaults.get("data").size() <= 10, path);
            for (String invalid : List.of("page=0", "page=-1", "size=0", "size=101", "page=abc")) {
                var response = http("GET", path + "?" + invalid, token);
                assertEquals(400, response.statusCode(), path + "?" + invalid + ": " + response.body());
            }
        }
        assertEquals(404, http("GET", "/api/admin/flash-sales/9223372036854775807/items", token).statusCode());
    }

    @Test
    void explicitStatusUpdatesAreIdempotentAndRevokeOnlyOnLocking() throws Exception {
        String adminToken = adminToken();
        User customer = user();
        var session = login(customer);
        String path = "/api/admin/users/" + customer.getId() + "/status";
        for (int i = 0; i < 2; i++) {
            var response = http("PUT", path, adminToken, "{\"isActive\":false}");
            assertEquals(200, response.statusCode(), response.body());
            var profile = json.readTree(response.body()).get("result");
            assertFalse(profile.get("isActive").asBoolean());
            assertFalse(profile.has("active"));
            assertFalse(profile.has("password"));
            assertFalse(profile.has("tokenVersion"));
        }
        assertEquals(1, users.findById(customer.getId()).orElseThrow().getTokenVersion());
        assertEquals(401, http("GET", "/api/details/profile", session.accessToken()).statusCode());
        assertEquals(200, http("PUT", path, adminToken, "{\"isActive\":true}").statusCode());
        var newSession = login(customer);
        assertEquals(200, http("PUT", "/api/users/" + customer.getId() + "/status",
                adminToken, "{\"isActive\":true}").statusCode());
        assertEquals(1, users.findById(customer.getId()).orElseThrow().getTokenVersion());
        assertEquals(200, http("GET", "/api/details/profile", newSession.accessToken()).statusCode());
        assertEquals(401, http("GET", "/api/details/profile", session.accessToken()).statusCode());
    }

    @Test
    void statusEndpointRejectsInvalidRequestsAndProtectsAdmins() throws Exception {
        User admin = user();
        admin.setRole(UserRole.ADMIN);
        users.saveAndFlush(admin);
        String token = login(admin).accessToken();
        String path = "/api/admin/users/" + admin.getId() + "/status";
        for (String body : List.of("{}", "{\"isActive\":null}", "{\"isActive\":false}")) {
            assertEquals(400, http("PUT", path, token, body).statusCode(), body);
        }
        assertTrue(users.findById(admin.getId()).orElseThrow().isActive());
        assertEquals(404, http("PUT", "/api/admin/users/9223372036854775807/status", token,
                "{\"isActive\":false}").statusCode());
    }

    @Test
    void adminUserCrudUsesCanonicalRoutesAndKeepsAliases() throws Exception {
        String token = adminToken();
        String name = "crud_" + UUID.randomUUID().toString().substring(0, 12);
        String body = json.writeValueAsString(new com.example.demo.user.dto_request.UserCreationRequest(
                name, name + "@example.test", PASSWORD));
        var created = http("POST", "/api/admin/users", token, body);
        assertEquals(201, created.statusCode(), created.body());
        var profile = json.readTree(created.body()).get("result");
        assertTrue(profile.get("isActive").asBoolean());
        assertEquals("CUSTOMER", profile.get("role").asString());
        long id = profile.get("id").asLong();
        assertEquals(200, http("GET", "/api/admin/users/" + id, token).statusCode());
        assertEquals(200, http("GET", "/api/users/" + id, token).statusCode());
        var updated = http("PUT", "/api/admin/users/" + id, token,
                "{\"email\":\"updated_" + name + "@example.test\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("updated_" + name + "@example.test",
                json.readTree(updated.body()).get("result").get("email").asString());
        assertEquals(200, http("DELETE", "/api/admin/users/" + id, token).statusCode());
        assertEquals(404, http("GET", "/api/users/" + id, token).statusCode());
    }

    @Test
    void userFiltersValidateRolesAndTreatSearchWildcardsLiterally() throws Exception {
        String token = adminToken();
        User customer = user();
        String prefix = "filter" + UUID.randomUUID().toString().substring(0, 8);
        customer.setUsername(prefix + "_test");
        users.saveAndFlush(customer);
        User other = user();
        other.setUsername(prefix + "Xtest");
        users.saveAndFlush(other);
        String keyword = java.net.URLEncoder.encode("  " + prefix.toUpperCase(java.util.Locale.ROOT) + "_  ",
                java.nio.charset.StandardCharsets.UTF_8);
        var response = http("GET", "/api/admin/users?role=CUSTOMER&isActive=true&keyword=" + keyword, token);
        assertEquals(200, response.statusCode(), response.body());
        var page = json.readTree(response.body()).get("result");
        assertEquals(1, page.get("totalElements").asInt());
        assertEquals(customer.getId().longValue(), page.get("data").get(0).get("id").asLong());
        for (String literal : List.of("%", "!")) {
            var literalResponse = http("GET", "/api/admin/users?keyword=" + prefix
                    + java.net.URLEncoder.encode(literal, java.nio.charset.StandardCharsets.UTF_8), token);
            assertEquals(0, json.readTree(literalResponse.body()).get("result").get("totalElements").asInt());
        }
        assertEquals(400, http("GET", "/api/admin/users?role=INVALID", token).statusCode());
        var empty = json.readTree(http("GET", "/api/users?keyword=" + prefix + "&page=100&size=1", token).body()).get("result");
        assertEquals(100, empty.get("currentPage").asInt());
        assertEquals(0, empty.get("data").size());
    }

    @Test
    void adminRoutesRejectAnonymousAndCustomerRequests() throws Exception {
        User customer = user();
        String token = login(customer).accessToken();
        for (String path : List.of("/api/admin/users", "/api/users", "/api/admin/categories",
                "/api/admin/products", "/api/admin/inventories", "/api/admin/vouchers", "/api/admin/orders",
                "/api/admin/flash-sales", "/api/admin/flash-sales/active", "/api/admin/flash-sales/1/items")) {
            assertEquals(401, http("GET", path, "").statusCode(), path);
            assertEquals(403, http("GET", path, token).statusCode(), path);
        }
        String path = "/api/admin/users/" + customer.getId() + "/status";
        assertEquals(401, http("PUT", path, "", "{\"isActive\":false}").statusCode());
        assertEquals(403, http("PUT", path, token, "{\"isActive\":false}").statusCode());
    }

    private String adminToken() {
        User admin = user();
        admin.setRole(UserRole.ADMIN);
        users.saveAndFlush(admin);
        return login(admin).accessToken();
    }

    private User user() {
        String name = "test_" + UUID.randomUUID().toString().substring(0, 12);
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.test")
                .password(passwordEncoder.encode(PASSWORD)).role(UserRole.CUSTOMER).build());
    }

    private AuthService.Session login(User user) {
        return auth.login(new LoginRequest(user.getUsername(), PASSWORD));
    }

    private Product product(int stock) {
        Product product = products.saveAndFlush(Product.builder().name("Test " + UUID.randomUUID())
                .price(new BigDecimal("100")).status(ProductStatus.ACTIVE).build());
        inventories.saveAndFlush(Inventory.builder().product(product).availableQuantity(stock).lockedQuantity(0).build());
        return product;
    }

    private void cart(User customer, Product... items) {
        Cart cart = Cart.builder().userId(customer.getId()).build();
        for (Product product : items) {
            cart.addItem(CartItem.builder().product(product).quantity(1).build());
        }
        carts.saveAndFlush(cart);
    }

    private OrderRequest request(String code) {
        return new OrderRequest("Test customer", "0901234567", "Test address", code);
    }

    private Voucher voucher(DiscountType type, String amount, String cap, int quantity) {
        return vouchers.saveAndFlush(Voucher.builder().code("TEST" + UUID.randomUUID().toString().substring(0, 12))
                .discountType(type).discountValue(new BigDecimal(amount))
                .maxDiscountAmount(cap == null ? null : new BigDecimal(cap)).minOrderValue(BigDecimal.ZERO)
                .totalQuantity(quantity).startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1)).build());
    }

    private void saveVoucher(User customer, Voucher voucher) {
        userVouchers.saveAndFlush(UserVoucher.builder().userId(customer.getId()).voucher(voucher).build());
    }

    private FlashSaleItem promotion(Product product, int quota) {
        FlashSale sale = flashSales.saveAndFlush(FlashSale.builder().name("Test flash sale")
                .startTime(LocalDateTime.now().minusMinutes(1)).endTime(LocalDateTime.now().plusHours(1))
                .status(FlashSaleStatus.ONGOING).build());
        return flashSaleItems.saveAndFlush(FlashSaleItem.builder().flashSale(sale).product(product)
                .promotionalPrice(new BigDecimal("30")).totalQuantity(quota).build());
    }

    private void assertStock(Product product, int available, int held) {
        Inventory inventory = inventories.findByProductId(product.getId()).orElseThrow();
        assertEquals(available, inventory.getAvailableQuantity());
        assertEquals(held, inventory.getLockedQuantity());
    }

    private void assertDiscount(DiscountType type, String amount, String cap, String discount, String total) {
        User customer = user();
        Product product = product(1);
        Voucher voucher = voucher(type, amount, cap, 1);
        saveVoucher(customer, voucher);
        cart(customer, product);
        OrderResponse order = checkout.placeOrder(customer.getId(), request(voucher.getCode()));
        assertEquals(0, order.getDiscountAmount().compareTo(new BigDecimal(discount)));
        assertEquals(0, order.getFinalAmount().compareTo(new BigDecimal(total)));
    }

    private HttpResponse<String> http(String method, String path, String token) throws Exception {
        return http(method, path, token, null);
    }

    private HttpResponse<String> http(String method, String path, String token, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void fundWallet(User user) {
        Long walletId = walletService.initializeWallet(user.getId()).getId();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var wallet = wallets.findByIdForUpdate(walletId).orElseThrow();
            wallet.setBalance(new BigDecimal("1000"));
            walletSecurity.saveAndFlush(com.example.demo.wallet.entity.WalletSecurity.builder().wallet(wallet)
                    .pinHash(passwordEncoder.encode("123456")).build());
        });
    }

    private com.example.demo.wallet.dto.PaymentResponse pay(User user, Long orderId) {
        var principal = new com.example.demo.user.security.UserPrincipal(user);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        try {
            var request = new com.example.demo.wallet.dto.PayOrderRequest();
            request.setPin("123456");
            return walletPayments.payOrder(orderId, request, "pay:" + orderId);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private record Result<T>(T value, ErrorCode error) {
        boolean success() { return error == null; }
    }

    private <T> List<Result<T>> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var firstResult = executor.submit(() -> runTogether(first, ready, start));
            var secondResult = executor.submit(() -> runTogether(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not reach the start barrier");
            start.countDown();
            return List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private <T> Result<T> runTogether(Callable<T> operation, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start");
        }
        try {
            return new Result<>(operation.call(), null);
        } catch (AppException exception) {
            return new Result<>(null, exception.getErrorCode());
        }
    }
}
