package com.example.demo;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.entity.*;
import com.example.demo.user.exception.*;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.wallet.dto.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.gateway.WalletRecoveryDelivery;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;
import tools.jackson.databind.ObjectMapper;
@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers=IsolatedMySqlDatabase.class)
@Import({WalletSecurityFeaturesIntegrationTests.MailConfig.class,WalletFundingIntegrationTests.GatewayConfiguration.class})
class WalletSecurityFeaturesIntegrationTests {
    @TestConfiguration static class MailConfig {@Bean CapturedMail capturedMail(){return new CapturedMail();}}
    static class CapturedMail implements WalletRecoveryDelivery {
        String to,code;boolean fail;
        public void send(String recipient,String code,LocalDateTime expires){if(fail){fail=false;throw new IllegalStateException("Delivery failed");}this.to=recipient;this.code=code;}
    }
    @Autowired UserRepository users; @Autowired WalletRepository wallets; @Autowired WalletSecurityRepository security;
    @Autowired WalletPinRecoveryRepository challenges; @Autowired WalletService walletService; @Autowired WalletPinService pins;
    @Autowired WalletRecoveryService recovery; @Autowired CapturedMail mail; @Autowired PasswordEncoder encoder;
    @Autowired WalletFundingService funding; @Autowired WalletBankManagementService banks; @Autowired WalletBankWorker worker;
    @Autowired BankTransferRepository transfers; @Autowired WalletNotificationService notifications;
    @Autowired WalletNotificationRepository notificationRows; @Autowired PlatformTransactionManager manager; @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager entities;
    private User owner;private Long walletId;
    @BeforeEach void setup(){
        String n="security_"+UUID.randomUUID().toString().replace("-","");owner=users.saveAndFlush(User.builder().username(n).email(n+"@example.test").password(encoder.encode("password123")).role(UserRole.CUSTOMER).build());
        authenticate(owner);walletId=walletService.initializeMyWallet().getId();
        var request=new SetupPinRequest();request.setCurrentPassword("password123");request.setNewPin("123456");request.setConfirmPin("123456");pins.setupPin(request);
        new TransactionTemplate(manager).executeWithoutResult(tx->wallets.findByIdForUpdate(walletId).orElseThrow().setBalance(new BigDecimal("100")));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void resetUsesProtectedEmailConsumesCodeRevokesSessionsAndBlocksWithdrawals(){
        String protectedEmail=owner.getEmail();owner.setEmail("changed-"+UUID.randomUUID()+"@example.test");users.saveAndFlush(owner);
        var start=recovery.requestReset();assertEquals(protectedEmail,mail.to);assertFalse(json.writeValueAsString(start).contains(mail.code));
        var stored=challenges.findById(start.challengeId()).orElseThrow();assertNotEquals(mail.code,stored.getCodeHash());
        recovery.resetPin(new WalletRecoveryDtos.Confirm(start.challengeId(),mail.code,"654321","654321"));
        var row=security.findById(walletId).orElseThrow();assertTrue(encoder.matches("654321",row.getPinHash()));assertTrue(row.getWithdrawalBlockedUntil().isAfter(LocalDateTime.now().plusHours(23)));
        assertTrue(users.findById(owner.getId()).orElseThrow().getTokenVersion()>owner.getTokenVersion());
        error(ErrorCode.TOKEN_INVALID,()->recovery.requestReset());authenticate(users.findById(owner.getId()).orElseThrow());
        error(ErrorCode.WALLET_RECOVERY_INVALID,()->recovery.resetPin(new WalletRecoveryDtos.Confirm(start.challengeId(),mail.code,"111111","111111")));
        Long account=account();var withdraw=withdraw(account);withdraw.setPin("654321");error(ErrorCode.WALLET_WITHDRAWAL_COOLDOWN,()->funding.withdraw(withdraw,"after-reset"));
        assertEquals(1,notificationRows.countByEventKey("security:"+start.challengeId()));
    }
    @Test void wrongOtpCommitsAttemptsAndCannotBeReusedAfterFiveFailures(){
        var start=recovery.requestReset();String bad=mail.code.equals("00000000")?"11111111":"00000000";
        for(int i=0;i<5;i++)error(ErrorCode.WALLET_RECOVERY_INVALID,()->recovery.resetPin(new WalletRecoveryDtos.Confirm(start.challengeId(),bad,"654321","654321")));
        var row=challenges.findById(start.challengeId()).orElseThrow();assertEquals(5,row.getFailedAttempts());assertNotNull(row.getConsumedAt());
        error(ErrorCode.WALLET_RECOVERY_INVALID,()->recovery.resetPin(new WalletRecoveryDtos.Confirm(start.challengeId(),mail.code,"654321","654321")));
        error(ErrorCode.WALLET_RECOVERY_RATE_LIMIT,()->recovery.requestReset());
    }
    @Test void failedDeliveryInvalidatesChallengeAndDoesNotExposeProviderFailure(){
        mail.fail=true;error(ErrorCode.WALLET_RECOVERY_UNAVAILABLE,()->recovery.requestReset());
        assertNotNull(challenges.findFirstByWalletIdOrderByCreatedAtDesc(walletId).orElseThrow().getConsumedAt());
    }
    @Test void expiredCodeCannotResetPin(){
        var start=recovery.requestReset();new TransactionTemplate(manager).executeWithoutResult(tx->entities.createQuery("update WalletPinRecovery c set c.expiresAt=:time where c.id=:id").setParameter("time",LocalDateTime.now().minusMinutes(1)).setParameter("id",start.challengeId()).executeUpdate());
        error(ErrorCode.WALLET_RECOVERY_INVALID,()->recovery.resetPin(new WalletRecoveryDtos.Confirm(start.challengeId(),mail.code,"654321","654321")));assertTrue(encoder.matches("123456",security.findById(walletId).orElseThrow().getPinHash()));
    }
    @Test void changingProtectedRecoveryEmailRequiresPinAndOtp(){
        String updated="verified-"+UUID.randomUUID()+"@example.test";owner.setEmail(updated);users.saveAndFlush(owner);
        var start=recovery.requestRecoveryEmail(new WalletRecoveryDtos.Enroll("123456"));assertEquals(updated,mail.to);assertNotEquals(updated,security.findById(walletId).orElseThrow().getRecoveryEmail());
        recovery.confirmRecoveryEmail(new WalletRecoveryDtos.EmailConfirm(start.challengeId(),mail.code));assertEquals(updated,security.findById(walletId).orElseThrow().getRecoveryEmail());
    }
    @Test void cancelledUnsentWithdrawalReleasesOnceEvenWhileWalletFrozen(){
        var result=funding.withdraw(withdraw(account()),"cancel");var freeze=new WalletFreezeRequest();freeze.setReason("Lost device");walletService.freezeMyWallet(freeze);
        var action=new WalletBankActionRequest("123456","Cancel pending withdrawal");banks.cancelWithdrawal(result.getTransactionId(),action);banks.cancelWithdrawal(result.getTransactionId(),action);
        assertEquals(0,new BigDecimal("100").compareTo(wallets.findById(walletId).orElseThrow().getBalance()));assertEquals(0,wallets.findById(walletId).orElseThrow().getPendingBalance().signum());
        notifications.publishTransaction(result.getTransactionId());notifications.publishTransaction(result.getTransactionId());assertEquals(1,notificationRows.countByEventKey("transaction:"+result.getTransactionId()));
    }
    @Test void dispatchedWithdrawalCannotBeCancelledAndRevokedBankCannotBeUsed(){
        Long account=account();var result=funding.withdraw(withdraw(account),"pending");
        new TransactionTemplate(manager).executeWithoutResult(tx->{var t=transfers.findByTransactionId(result.getTransactionId()).orElseThrow();t.setDispatchStartedAt(LocalDateTime.now());t.setAttempts(1);});
        error(ErrorCode.WALLET_TRANSFER_ALREADY_DISPATCHED,()->banks.cancelWithdrawal(result.getTransactionId(),new WalletBankActionRequest("123456","Cancel")));
        banks.revoke(account,new WalletBankActionRequest("123456","Bank no longer used"));
        error(ErrorCode.WALLET_BANK_ACCOUNT_NOT_FOUND,()->funding.withdraw(withdraw(account),"revoked"));
        assertEquals(0,new BigDecimal("20").compareTo(wallets.findById(walletId).orElseThrow().getPendingBalance()));
    }
    private Long account(){var request=new LinkBankAccountRequest();request.setVerificationToken("verified:"+owner.getId());return funding.linkAccount(request).getLinkedBankAccountId();}
    private WithdrawRequest withdraw(Long id){var request=new WithdrawRequest();request.setLinkedBankAccountId(id);request.setAmount(new BigDecimal("20"));request.setPin("123456");return request;}
    private void authenticate(User user){var p=new UserPrincipal(user);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    private void error(ErrorCode code,org.junit.jupiter.api.function.Executable task){assertEquals(code,assertThrows(AppException.class,task).getErrorCode());}
}
