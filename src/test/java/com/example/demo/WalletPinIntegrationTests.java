package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.api.dto.UserCreationRequest;
import com.example.demo.user.entity.User;
import com.example.demo.user.api.UserRole;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.user.service.AuthService;
import com.example.demo.wallet.api.dto.ChangePinRequest;
import com.example.demo.wallet.api.dto.SetupPinRequest;
import com.example.demo.wallet.repository.WalletRepository;
import com.example.demo.wallet.repository.WalletSecurityRepository;
import com.example.demo.wallet.service.WalletPinService;
import com.example.demo.wallet.service.WalletService;

@SpringBootTest(properties = {"debug=false", "logging.level.org.springframework=WARN", "logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers = IsolatedMySqlDatabase.class)
class WalletPinIntegrationTests {
    @Autowired WalletPinService pins;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean WalletService walletService;
    @Autowired AuthService auth;
    @Autowired com.example.demo.user.api.RegistrationUseCase registration;
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired WalletSecurityRepository security;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    private Long walletId;

    @BeforeEach
    void prepare() {
        String name = "pin_" + UUID.randomUUID().toString().replace("-", "");
        User user = users.saveAndFlush(User.builder().username(name).email(name + "@example.test")
                .password(encoder.encode("password123")).role(UserRole.CUSTOMER).build());
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        walletId = walletService.initializeMyWallet().getId();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void setupHashesPinAndCannotOverwriteExistingPin() {
        assertFalse(pins.getMySecurity().isPinConfigured());
        assertFalse(security.existsById(walletId));
        assertTrue(pins.setupPin(setup("password123")).isPinConfigured());
        var row = security.findById(walletId).orElseThrow();
        assertNotEquals("123456", row.getPinHash());
        assertTrue(encoder.matches("123456", row.getPinHash()));
        assertError(ErrorCode.WALLET_PIN_ALREADY_CONFIGURED, () -> pins.setupPin(setup("password123")));
    }

    @Test
    void failedPasswordSetupIsRateLimitedAndCommitsCounter() {
        for (int i = 0; i < 4; i++) {
            assertError(ErrorCode.OLD_PASSWORD_INCORRECT, () -> pins.setupPin(setup("wrong-password")));
        }
        assertError(ErrorCode.WALLET_PIN_LOCKED, () -> pins.setupPin(setup("wrong-password")));
        assertEquals(5, security.findById(walletId).orElseThrow().getFailedPinAttempts());
        assertError(ErrorCode.WALLET_PIN_LOCKED, () -> pins.setupPin(setup("password123")));
    }

    @Test
    void failedPinPersistsEvenWhenCallerRollsBack() {
        pins.setupPin(setup("password123"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(AppException.class, () -> transaction.execute(status -> pins.authorize("000000", "PAY:1")));
        assertEquals(1, security.findById(walletId).orElseThrow().getFailedPinAttempts());
    }

    @Test
    void lockCannotBeBypassedWithCorrectPinAndExpiredLockAllowsRetry() {
        pins.setupPin(setup("password123"));
        for (int i = 0; i < 4; i++) assertError(ErrorCode.WALLET_PIN_INCORRECT, () -> pins.changePin(change("000000")));
        assertError(ErrorCode.WALLET_PIN_LOCKED, () -> pins.changePin(change("000000")));
        assertError(ErrorCode.WALLET_PIN_LOCKED, () -> pins.changePin(change("123456")));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var row = security.findByWalletIdForUpdate(walletId).orElseThrow();
            row.setPinLockedUntil(LocalDateTime.now().minusSeconds(1));
        });
        pins.changePin(change("123456"));
        var row = security.findById(walletId).orElseThrow();
        assertEquals(0, row.getFailedPinAttempts());
        assertNull(row.getPinLockedUntil());
        assertTrue(encoder.matches("654321", row.getPinHash()));
        assertError(ErrorCode.WALLET_PIN_INCORRECT, () -> pins.authorize("123456", "PAY:1"));
        assertNotNull(pins.authorize("654321", "PAY:1"));
    }

    @Test
    void missingPinAndMismatchedConfirmationAreRejected() {
        assertError(ErrorCode.WALLET_PIN_NOT_CONFIGURED, () -> pins.authorize("123456", "PAY:1"));
        var request = setup("password123");
        request.setConfirmPin("111111");
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> pins.setupPin(request));
        assertFalse(security.findById(walletId).orElseThrow().getPinHash() != null);
    }

    @Test
    void registrationCreatesExactlyOneWallet() {
        String name = "reg_" + UUID.randomUUID().toString().replace("-", "");
        registration.register(new UserCreationRequest(name, name + "@example.test", "password123"));
        User created = users.findByUsername(name).orElseThrow();
        assertTrue(wallets.existsByUserId(created.getId()));
        assertError(ErrorCode.USERNAME_EXISTED,
                () -> registration.register(new UserCreationRequest(name, name + "@example.test", "password123")));
    }

    @Test void walletProvisioningFailureRollsBackNewAccount(){
        String name="failed_reg_"+UUID.randomUUID().toString().replace("-","");
        long before=wallets.count();
        WalletService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(walletService);
        org.mockito.Mockito.doThrow(new AppException(ErrorCode.WALLET_NOT_FOUND)).when(target).initializeWallet(org.mockito.ArgumentMatchers.anyLong());
        try{
            assertThrows(AppException.class,()->registration.register(new UserCreationRequest(name,name+"@example.test","password123")));
            assertFalse(users.existsByUsername(name));assertEquals(before,wallets.count());
        }finally{org.mockito.Mockito.reset(target);}
    }

    @Test void callerRollbackRemovesBothAccountAndWallet(){
        String name="rollback_reg_"+UUID.randomUUID().toString().replace("-","");long before=wallets.count();
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            registration.register(new UserCreationRequest(name,name+"@example.test","password123"));throw new IllegalStateException("Caller failed");
        }));
        assertFalse(users.existsByUsername(name));assertEquals(before,wallets.count());
    }

    private SetupPinRequest setup(String password) {
        SetupPinRequest request = new SetupPinRequest();
        request.setCurrentPassword(password);
        request.setNewPin("123456");
        request.setConfirmPin("123456");
        return request;
    }

    private ChangePinRequest change(String current) {
        ChangePinRequest request = new ChangePinRequest();
        request.setCurrentPin(current);
        request.setNewPin("654321");
        request.setConfirmPin("654321");
        return request;
    }

    private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(AppException.class, action).getErrorCode());
    }
}
