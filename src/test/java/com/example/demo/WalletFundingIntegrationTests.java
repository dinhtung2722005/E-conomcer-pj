package com.example.demo;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.WalletHoldStatus;
import com.example.demo.wallet.api.WalletStatus;
import com.example.demo.user.api.UserRole;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import com.example.demo.wallet.api.dto.WithdrawRequest;
import com.example.demo.wallet.api.dto.TopUpRequest;
import com.example.demo.wallet.api.dto.TopUpInstructionsResponse;
import com.example.demo.wallet.api.dto.SetupPinRequest;
import com.example.demo.wallet.api.dto.LinkBankAccountRequest;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.*;
import com.example.demo.shared.error.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.gateway.BankGateway;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;

@SpringBootTest(properties = {"debug=false", "wallet.bank.worker-enabled=false",
        "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
@Import(WalletFundingIntegrationTests.GatewayConfiguration.class)
class WalletFundingIntegrationTests {
    @TestConfiguration static class GatewayConfiguration {
        @Bean ScriptedBank bank() { return new ScriptedBank(); }
    }
    /** Test source only. No simulated gateway is installed in the application. */
    static class ScriptedBank implements BankGateway {
        final ConcurrentMap<String, State> states = new ConcurrentHashMap<>();
        final java.util.Set<String> timeouts = ConcurrentHashMap.newKeySet();
        final java.util.Set<String> wrongAmounts = ConcurrentHashMap.newKeySet();
        final java.util.Set<String> references = ConcurrentHashMap.newKeySet();
        @Override public String provider() { return "TEST_BANK"; }
        @Override public VerifiedAccount verifyOwnership(Long userId, String proof) {
            if (!proof.equals("verified:" + userId)) throw new AppException(ErrorCode.ACCESS_DENIED);
            return new VerifiedAccount("account:" + userId, "TEST", "Test bank", "****1234", "Test owner");
        }
        @Override public Result resolve(Command command) {
            references.add(command.merchantReference());
            if (timeouts.contains(command.merchantReference())) throw new IllegalStateException("Unknown outcome");
            var instructions = new TopUpInstructionsResponse();
            instructions.setTransferContent(command.merchantReference());
            instructions.setAccountNumber("test-receiving-account");
            return new Result(command.merchantReference(), wrongAmounts.contains(command.merchantReference())
                    ? command.amount().add(BigDecimal.ONE) : command.amount(), command.currency(), command.accountToken(),
                    "bank:" + command.merchantReference(), states.getOrDefault(command.merchantReference(), State.PROCESSING),
                    instructions, LocalDateTime.now().plusHours(1));
        }
    }

    @Autowired WalletFundingService funding;
    @Autowired WalletPinService pins;
    @Autowired WalletService walletService;
    @Autowired WalletBankWorker worker;
    @Autowired ScriptedBank bank;
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired BankTransferRepository transfers;
    @Autowired WalletHoldRepository holds;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    private User owner;
    private Long walletId;
    private Long accountId;

    @BeforeEach void prepare() {
        owner = createUser();
        authenticate(owner);
        walletId = walletService.initializeMyWallet().getId();
        SetupPinRequest setup = new SetupPinRequest();
        setup.setCurrentPassword("password123"); setup.setNewPin("123456"); setup.setConfirmPin("123456");
        pins.setupPin(setup);
        LinkBankAccountRequest link = new LinkBankAccountRequest();
        link.setVerificationToken("verified:" + owner.getId());
        accountId = funding.linkAccount(link).getLinkedBankAccountId();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void ownershipMustBeVerifiedAndCannotReadAnotherUsersTransfer() {
        LinkBankAccountRequest link = new LinkBankAccountRequest();
        link.setVerificationToken("verified:" + (owner.getId() + 1));
        error(ErrorCode.ACCESS_DENIED, () -> funding.linkAccount(link));
        var response = funding.topUp(topUp("100"), "deposit");
        authenticate(createUser());
        error(ErrorCode.WALLET_TRANSACTION_NOT_FOUND, () -> funding.getTopUp(response.getTransactionId()));
    }

    @Test void topUpCreditsOnlyVerifiedSuccessOnceEvenWhenWalletLocked() {
        var response = funding.topUp(topUp("100"), "deposit");
        var transfer = transfer(response.getTransactionId());
        worker.process(transfer.getId());
        money("0", wallet().getBalance());
        assertEquals(TransactionStatus.PROCESSING, funding.getTopUp(response.getTransactionId()).getStatus());
        assertNotNull(funding.getTopUp(response.getTransactionId()).getInstructions());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                wallets.findByIdForUpdate(walletId).orElseThrow().setStatus(WalletStatus.LOCKED));
        bank.states.put(transfer.getMerchantReference(), BankGateway.State.SUCCEEDED);
        makeDue(transfer.getId());
        worker.process(transfer.getId()); worker.process(transfer.getId());
        money("100", wallet().getBalance());
        assertEquals(response.getTransactionId(), funding.topUp(topUp("100.00"), "deposit").getTransactionId());
        money("100", wallet().getBalance());
    }

    @Test void sameKeyWithChangedPayloadIsRejected() {
        funding.topUp(topUp("100"), "deposit");
        error(ErrorCode.WALLET_IDEMPOTENCY_CONFLICT, () -> funding.topUp(topUp("101"), "deposit"));
    }

    @Test void mismatchedBankAmountNeverCreditsAndRemainsForReconciliation() {
        var transfer = transfer(funding.topUp(topUp("100"), "deposit").getTransactionId());
        bank.states.put(transfer.getMerchantReference(), BankGateway.State.SUCCEEDED);
        bank.wrongAmounts.add(transfer.getMerchantReference());
        worker.process(transfer.getId());
        money("0", wallet().getBalance());
        assertFalse(transfers.findById(transfer.getId()).orElseThrow().isFinished());
        assertEquals("INVALID_BANK_RESULT", transfers.findById(transfer.getId()).orElseThrow().getLastError());
    }

    @Test void timeoutRetainsWithdrawalHoldAndLaterSuccessCapturesExactlyOnce() {
        credit("100");
        var response = funding.withdraw(withdraw("80"), "withdraw");
        var transfer = transfer(response.getTransactionId());
        money("20", wallet().getBalance()); money("80", wallet().getPendingBalance());
        assertEquals(response.getTransactionId(), funding.withdraw(withdraw("80.00"), "withdraw").getTransactionId());
        bank.timeouts.add(transfer.getMerchantReference());
        worker.process(transfer.getId());
        assertEquals(TransactionStatus.PROCESSING, funding.getWithdrawal(response.getTransactionId()).getStatus());
        money("80", wallet().getPendingBalance());
        bank.timeouts.remove(transfer.getMerchantReference());
        bank.states.put(transfer.getMerchantReference(), BankGateway.State.SUCCEEDED);
        makeDue(transfer.getId());
        worker.process(transfer.getId()); worker.process(transfer.getId());
        money("20", wallet().getBalance()); money("0", wallet().getPendingBalance());
        assertEquals(WalletHoldStatus.CAPTURED, holds.findById(response.getHoldId()).orElseThrow().getStatus());
    }

    @Test void definitiveBankRejectionReleasesOnlyTheWithdrawalHold() {
        credit("100");
        var response = funding.withdraw(withdraw("80"), "withdraw");
        var transfer = transfer(response.getTransactionId());
        bank.states.put(transfer.getMerchantReference(), BankGateway.State.REJECTED);
        worker.process(transfer.getId()); worker.process(transfer.getId());
        money("100", wallet().getBalance()); money("0", wallet().getPendingBalance());
        assertEquals(TransactionStatus.FAILED, funding.getWithdrawal(response.getTransactionId()).getStatus());
        assertEquals(WalletHoldStatus.RELEASED, holds.findById(response.getHoldId()).orElseThrow().getStatus());
    }

    @Test void concurrentWithdrawalsCannotOverspend() throws Exception {
        credit("100");
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> action = () -> {
                authenticate(owner); start.await();
                try { funding.withdraw(withdraw("80"), UUID.randomUUID().toString()); return true; }
                catch (AppException exception) {
                    assertEquals(ErrorCode.WALLET_INSUFFICIENT_BALANCE, exception.getErrorCode()); return false;
                } finally { SecurityContextHolder.clearContext(); }
            };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        money("20", wallet().getBalance()); money("80", wallet().getPendingBalance());
    }

    @Test void lockedWalletAndInsufficientCashCannotWithdrawBonus() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var wallet = wallets.findByIdForUpdate(walletId).orElseThrow();
            wallet.setBonusBalance(new BigDecimal("100"));
        });
        error(ErrorCode.WALLET_INSUFFICIENT_BALANCE, () -> funding.withdraw(withdraw("10"), "withdraw"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                wallets.findByIdForUpdate(walletId).orElseThrow().setStatus(WalletStatus.LOCKED));
        error(ErrorCode.WALLET_LOCKED, () -> funding.withdraw(withdraw("10"), "withdraw"));
    }

    private User createUser() {
        String name = "bank_" + UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(User.builder().username(name).email(name + "@example.test")
                .password(encoder.encode("password123")).role(UserRole.CUSTOMER).build());
    }
    private void authenticate(User user) {
        var principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    private TopUpRequest topUp(String amount) {
        var request = new TopUpRequest(); request.setAmount(new BigDecimal(amount));
        request.setLinkedBankAccountId(accountId); return request;
    }
    private WithdrawRequest withdraw(String amount) {
        var request = new WithdrawRequest(); request.setAmount(new BigDecimal(amount));
        request.setLinkedBankAccountId(accountId); request.setPin("123456"); return request;
    }
    private void credit(String amount) {
        var transfer = transfer(funding.topUp(topUp(amount), "fund-wallet").getTransactionId());
        bank.states.put(transfer.getMerchantReference(), BankGateway.State.SUCCEEDED);
        worker.process(transfer.getId());
    }
    private BankTransfer transfer(Long transactionId) { return transfers.findByTransactionId(transactionId).orElseThrow(); }
    private Wallet wallet() { return wallets.findById(walletId).orElseThrow(); }
    private void makeDue(Long id) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                transfers.findByIdForUpdate(id).orElseThrow().setNextAttemptAt(LocalDateTime.now().minusSeconds(1)));
    }
    private void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private void error(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(AppException.class, action).getErrorCode());
    }
}
