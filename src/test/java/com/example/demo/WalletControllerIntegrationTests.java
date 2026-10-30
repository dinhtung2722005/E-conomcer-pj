package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.JwtUtils;
import com.example.demo.wallet.entity.TransactionStatus;
import com.example.demo.wallet.entity.TransactionType;
import com.example.demo.wallet.entity.Wallet;
import com.example.demo.wallet.entity.WalletHold;
import com.example.demo.wallet.entity.WalletHoldType;
import com.example.demo.wallet.entity.WalletStatus;
import com.example.demo.wallet.entity.WalletTransaction;
import com.example.demo.wallet.repository.WalletHoldRepository;
import com.example.demo.wallet.repository.WalletRepository;
import com.example.demo.wallet.repository.WalletStatusChangeRepository;
import com.example.demo.wallet.repository.WalletTransactionRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
class WalletControllerIntegrationTests {
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired com.example.demo.wallet.repository.WalletSecurityRepository pinSecurity;

    @Test
    void pinEndpointsHideSecretsAndPersistFailedAttempts() throws Exception {
        User customer = user(UserRole.CUSTOMER); Wallet wallet = wallet(customer); String token = jwt.generateToken(customer);
        assertStatus(401, http("POST", "/api/wallet/pin", null, "{}"));
        String setup = "{\"currentPassword\":\"password123\",\"newPin\":\"123456\",\"confirmPin\":\"123456\"}";
        var response = http("POST", "/api/wallet/pin", token, setup); assertStatus(200, response);
        assertTrue(result(response).get("pinConfigured").asBoolean());
        assertFalse(response.body().contains("123456")); assertFalse(response.body().contains("pinHash"));
        assertStatus(200, http("GET", "/api/wallet/security", token, null));
        String wrong = "{\"currentPin\":\"000000\",\"newPin\":\"654321\",\"confirmPin\":\"654321\"}";
        assertStatus(400, http("PUT", "/api/wallet/pin", token, wrong));
        assertEquals(1, pinSecurity.findById(wallet.getId()).orElseThrow().getFailedPinAttempts());
        String correct = "{\"currentPin\":\"123456\",\"newPin\":\"654321\",\"confirmPin\":\"654321\"}";
        assertStatus(200, http("PUT", "/api/wallet/pin", token, correct));
    }

    @Test
    void missingBankConfigurationCannotCreateMoneyAndIdempotencyHeaderIsRequired() throws Exception {
        User customer = user(UserRole.CUSTOMER); Wallet wallet = wallet(customer); String token = jwt.generateToken(customer);
        String body = "{\"amount\":100,\"linkedBankAccountId\":1}";
        assertStatus(400, http("POST", "/api/wallet/top-ups", token, body));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/wallet/top-ups"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .header("Idempotency-Key", "deposit").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertStatus(503, client.send(request, HttpResponse.BodyHandlers.ofString()));
        assertEquals(0, new BigDecimal("100").compareTo(wallets.findById(wallet.getId()).orElseThrow().getBalance()));
        assertStatus(403, http("GET", "/api/admin/wallets/" + wallet.getId() + "/reconciliation", token, null));
    }
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired WalletTransactionRepository transactions;
    @Autowired WalletHoldRepository holds;
    @Autowired WalletStatusChangeRepository audit;
    @Autowired JwtUtils jwt;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactionManager;
    @Value("${local.server.port}") int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void everyWalletEndpointRequiresAuthentication() throws Exception {
        for (String path : List.of("/api/wallet", "/api/wallet/transactions", "/api/wallet/transactions/1", "/api/wallet/holds")) {
            assertStatus(401, http("GET", path, null, null));
        }
        for (String path : List.of("/api/wallet", "/api/wallet/freeze",
                "/api/admin/wallets/1/freeze", "/api/admin/wallets/1/unfreeze")) {
            assertStatus(401, http("POST", path, null, "{\"reason\":\"test\"}"));
        }
    }

    @Test
    void initializationIsIdempotentAndNeverTakesOwnerOrBalancesFromClient() throws Exception {
        User owner = user(UserRole.CUSTOMER);
        User other = user(UserRole.CUSTOMER);
        Wallet foreign = wallet(other);
        String token = jwt.generateToken(owner);
        assertStatus(404, http("GET", "/api/wallet", token, null));
        assertFalse(wallets.existsByUserId(owner.getId()));

        var created = http("POST", "/api/wallet?userId=" + other.getId(), token,
                "{\"userId\":" + other.getId() + ",\"balance\":999999}");
        assertStatus(200, created);
        JsonNode result = result(created);
        Long walletId = wallets.findByUserId(owner.getId()).orElseThrow().getId();
        assertEquals(walletId.longValue(), result.get("id").asLong());
        assertNotEquals(foreign.getId().longValue(), result.get("id").asLong());
        assertEquals(0, result.get("balance").asLong());
        assertEquals(0, result.get("pendingBalance").asLong());
        assertEquals("VND", result.get("currency").asString());
        assertFalse(result.get("pinConfigured").asBoolean());
        var repeated = http("POST", "/api/wallet", token, null);
        assertStatus(200, repeated);
        assertEquals(walletId.longValue(), result(repeated).get("id").asLong());
        var read = http("GET", "/api/wallet?userId=" + other.getId(), token, null);
        assertStatus(200, read);
        assertEquals(walletId.longValue(), result(read).get("id").asLong());
        assertFalse(result(read).has("user"));
        assertFalse(result(read).has("pinHash"));
    }

    @Test
    void historyBindsFiltersAndPaginationAndTransactionDetailsRemainOwnerScoped() throws Exception {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        WalletTransaction first = deposit(wallet, TransactionStatus.SUCCESS);
        WalletTransaction second = deposit(wallet, TransactionStatus.SUCCESS);
        WalletTransaction end = deposit(wallet, TransactionStatus.PENDING);
        WalletTransaction foreign = deposit(wallet(user(UserRole.CUSTOMER)), TransactionStatus.SUCCESS);
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 10, 0);
        createdAt(first, from);
        createdAt(second, from);
        createdAt(end, from.plusHours(1));
        String token = jwt.generateToken(owner);
        String filters = "?type=DEPOSIT&status=SUCCESS&from=2026-01-01T10:00:00&to=2026-01-01T11:00:00&size=1";
        var firstPage = http("GET", "/api/wallet/transactions" + filters, token, null);
        assertStatus(200, firstPage);
        JsonNode page = result(firstPage);
        assertEquals(1, page.get("currentPage").asInt());
        assertEquals(2, page.get("totalElements").asInt());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals(second.getId().longValue(), page.get("data").get(0).get("id").asLong());
        var secondPage = http("GET", "/api/wallet/transactions" + filters + "&page=2", token, null);
        assertStatus(200, secondPage);
        assertEquals(first.getId().longValue(), result(secondPage).get("data").get(0).get("id").asLong());
        var own = http("GET", "/api/wallet/transactions/" + first.getId(), token, null);
        assertStatus(200, own);
        assertFalse(result(own).has("idempotencyKey"));
        assertFalse(result(own).has("wallet"));
        assertStatus(404, http("GET", "/api/wallet/transactions/" + foreign.getId(), token, null));
        assertStatus(404, http("GET", "/api/wallet/transactions/" + Long.MAX_VALUE, token, null));
    }

    @Test
    void holdsBindStatusFilterAndDoNotExposeAnotherWalletsFunds() throws Exception {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        hold(wallet, false);
        WalletHold partial = hold(wallet, true);
        hold(wallet(user(UserRole.CUSTOMER)), true);
        String token = jwt.generateToken(owner);
        var all = http("GET", "/api/wallet/holds", token, null);
        assertStatus(200, all);
        assertEquals(2, result(all).get("totalElements").asInt());
        var filtered = http("GET", "/api/wallet/holds?statuses=PARTIALLY_PROCESSED&page=1&size=1", token, null);
        assertStatus(200, filtered);
        assertEquals(1, result(filtered).get("totalElements").asInt());
        JsonNode entry = result(filtered).get("data").get(0);
        assertEquals(partial.getId().longValue(), entry.get("id").asLong());
        assertEquals(75, entry.get("remainingAmount").asLong());
        assertEquals("CASH", entry.get("balanceType").asString());
    }

    @Test
    void invalidQueryPathAndBodyReturnBadRequestWithoutChangingWallet() throws Exception {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        String token = jwt.generateToken(owner);
        for (String path : List.of(
                "/api/wallet/transactions?page=0", "/api/wallet/transactions?size=101",
                "/api/wallet/transactions?type=UNKNOWN", "/api/wallet/transactions?status=UNKNOWN",
                "/api/wallet/transactions?from=bad-date",
                "/api/wallet/transactions?from=2026-01-02T00:00:00&to=2026-01-01T00:00:00",
                "/api/wallet/transactions/-1", "/api/wallet/transactions/not-a-number",
                "/api/wallet/holds?statuses=UNKNOWN", "/api/wallet/holds?statuses=", "/api/wallet/holds?size=0")) {
            assertStatus(400, http("GET", path, token, null));
        }
        for (String body : List.of("{}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "x".repeat(501) + "\"}", "{")) {
            assertStatus(400, http("POST", "/api/wallet/freeze", token, body));
        }
        assertStatus(400, http("POST", "/api/wallet/freeze", token, null));
        assertEquals(WalletStatus.ACTIVE, wallets.findById(wallet.getId()).orElseThrow().getStatus());
        assertEquals(0, audit.findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void customerFreezeIsIdempotentAuditedAndLeavesBalancesAndReadAccessIntact() throws Exception {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        String token = jwt.generateToken(owner);
        String body = "{\"reason\":\"  Bao mat tai khoan  \"}";
        var frozen = http("POST", "/api/wallet/freeze", token, body);
        assertStatus(200, frozen);
        assertEquals("LOCKED", result(frozen).get("status").asString());
        assertEquals(100, result(frozen).get("balance").asLong());
        assertStatus(200, http("POST", "/api/wallet/freeze", token, body));
        var changes = audit.findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), PageRequest.of(0, 10));
        assertEquals(1, changes.getTotalElements());
        assertEquals("Bao mat tai khoan", changes.getContent().getFirst().getReason());
        assertEquals(owner.getId(), changes.getContent().getFirst().getActor().getId());
        assertStatus(200, http("GET", "/api/wallet", token, null));
        assertStatus(200, http("GET", "/api/wallet/transactions", token, null));
    }

    @Test
    void onlyAdministratorsCanFreezeAndUnfreezeOtherWallets() throws Exception {
        User owner = user(UserRole.CUSTOMER);
        Wallet wallet = wallet(owner);
        String path = "/api/admin/wallets/" + wallet.getId();
        String body = "{\"reason\":\"Admin review\"}";
        String customerToken = jwt.generateToken(owner);
        assertStatus(403, http("POST", path + "/freeze", customerToken, body));
        assertStatus(403, http("POST", path + "/unfreeze", customerToken, body));
        assertEquals(WalletStatus.ACTIVE, wallets.findById(wallet.getId()).orElseThrow().getStatus());

        User admin = user(UserRole.ADMIN);
        String adminToken = jwt.generateToken(admin);
        var frozen = http("POST", path + "/freeze", adminToken, body);
        assertStatus(200, frozen);
        assertEquals("LOCKED", result(frozen).get("status").asString());
        var unfrozen = http("POST", path + "/unfreeze", adminToken, body);
        assertStatus(200, unfrozen);
        assertEquals("ACTIVE", result(unfrozen).get("status").asString());
        var changes = audit.findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), PageRequest.of(0, 10));
        assertEquals(2, changes.getTotalElements());
        assertTrue(changes.stream().allMatch(change -> change.getActor().getId().equals(admin.getId())
                && change.getActorRole() == UserRole.ADMIN));
        assertStatus(400, http("POST", "/api/admin/wallets/-1/freeze", adminToken, body));
        assertStatus(404, http("POST", "/api/admin/wallets/" + Long.MAX_VALUE + "/freeze", adminToken, body));
        assertStatus(400, http("POST", path + "/unfreeze", adminToken, "{}"));
    }

    private User user(UserRole role) {
        String suffix = UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().username("http-" + suffix).email(suffix + "@example.test")
                .password(passwordEncoder.encode("password123")).role(role).build());
    }

    private Wallet wallet(User user) {
        return wallets.saveAndFlush(Wallet.builder().userId(user.getId()).balance(new BigDecimal("100")).build());
    }

    private WalletTransaction deposit(Wallet wallet, TransactionStatus status) {
        return transactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).referenceId("business")
                .idempotencyKey(UUID.randomUUID().toString()).type(TransactionType.DEPOSIT).status(status)
                .amount(new BigDecimal("100")).build());
    }

    private WalletHold hold(Wallet wallet, boolean partial) {
        WalletHold hold = WalletHold.builder().wallet(wallet).referenceId(UUID.randomUUID().toString())
                .type(WalletHoldType.WITHDRAWAL).amount(new BigDecimal("100")).build();
        if (partial) {
            hold.capture(new BigDecimal("25"));
        }
        return holds.saveAndFlush(hold);
    }

    private void createdAt(WalletTransaction item, LocalDateTime time) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entities.createQuery("UPDATE WalletTransaction t SET t.createdAt = :time WHERE t.id = :id")
                        .setParameter("time", time).setParameter("id", item.getId()).executeUpdate());
    }

    private HttpResponse<String> http(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode result(HttpResponse<String> response) {
        return json.readTree(response.body()).get("result");
    }

    private void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
        assertEquals(expected, json.readTree(response.body()).get("code").asInt(), response.body());
    }
}
