package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.shared.error.*;
import com.example.demo.user.api.UserRole;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;

@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers=IsolatedMySqlDatabase.class)
@Import(WalletFundingIntegrationTests.GatewayConfiguration.class)
class WalletRiskIntegrationTests {
    @Autowired UserRepository users;@Autowired WalletRepository wallets;@Autowired WalletSecurityRepository security;
    @Autowired WalletService walletService;@Autowired WalletFundingService funding;@Autowired PasswordEncoder encoder;
    @Autowired WalletLimitReservationRepository quotas;@Autowired PlatformTransactionManager manager;
    private User owner;private Long walletId,accountId;
    @BeforeEach void setup(){
        String name="limits_"+UUID.randomUUID().toString().replace("-","");
        owner=users.saveAndFlush(User.builder().username(name).email(name+"@example.test").password("test-only").role(UserRole.CUSTOMER).build());
        authenticate();walletId=walletService.initializeWallet(owner.getId()).getId();
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            var wallet=wallets.findByIdForUpdate(walletId).orElseThrow();wallet.setBalance(new BigDecimal("100000000"));
            security.saveAndFlush(WalletSecurity.builder().wallet(wallet).pinHash(encoder.encode("123456")).build());
        });
        var link=new LinkBankAccountRequest();link.setVerificationToken("verified:"+owner.getId());accountId=funding.linkAccount(link).getLinkedBankAccountId();
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void fractionalVndAndExcessiveSingleTransfersDoNotConsumeQuota(){
        error(ErrorCode.WALLET_AMOUNT_INVALID,()->funding.topUp(topUp("1.5"),"fraction"));
        error(ErrorCode.WALLET_TRANSACTION_LIMIT,()->funding.topUp(topUp("50000001"),"large"));
        error(ErrorCode.WALLET_TRANSACTION_LIMIT,()->funding.withdraw(withdraw("20000001"),"large-withdraw"));
        assertEquals(0,quotas.countByWalletId(walletId));
        assertEquals(0,new BigDecimal("100000000").compareTo(wallets.findById(walletId).orElseThrow().getBalance()));
    }
    @Test void pendingTopUpsReserveDailyCapacityAndReplayDoesNotChargeAgain(){
        var first=funding.topUp(topUp("50000000"),"first");funding.topUp(topUp("50000000"),"second");
        error(ErrorCode.WALLET_DAILY_LIMIT,()->funding.topUp(topUp("1"),"third"));
        assertEquals(first.getTransactionId(),funding.topUp(topUp("50000000"),"first").getTransactionId());
        assertEquals(2,quotas.countByWalletId(walletId));
    }
    @Test void requestVelocityCountsOnlyNewCommittedRequests(){
        var first=funding.topUp(topUp("1"),"rate-0");
        for(int i=1;i<10;i++)funding.topUp(topUp("1"),"rate-"+i);
        error(ErrorCode.WALLET_REQUEST_RATE_LIMIT,()->funding.topUp(topUp("1"),"rate-10"));
        assertEquals(first.getTransactionId(),funding.topUp(topUp("1"),"rate-0").getTransactionId());
        assertEquals(10,quotas.countByWalletId(walletId));
    }
    @Test void concurrentWithdrawalsCannotExceedRemainingDailyCapacity()throws Exception {
        funding.withdraw(withdraw("20000000"),"initial-1");funding.withdraw(withdraw("20000000"),"initial-2");
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Callable<Boolean> attempt=()->{authenticate();start.await();try{funding.withdraw(withdraw("7000000"),UUID.randomUUID().toString());return true;}
                catch(AppException e){assertEquals(ErrorCode.WALLET_DAILY_LIMIT,e.getErrorCode());return false;}finally{SecurityContextHolder.clearContext();}};
            var first=executor.submit(attempt);var second=executor.submit(attempt);start.countDown();
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        assertEquals(3,quotas.countByWalletId(walletId));
        assertEquals(0,new BigDecimal("47000000").compareTo(wallets.findById(walletId).orElseThrow().getPendingBalance()));
    }
    private TopUpRequest topUp(String amount){var request=new TopUpRequest();request.setAmount(new BigDecimal(amount));request.setLinkedBankAccountId(accountId);return request;}
    private WithdrawRequest withdraw(String amount){var request=new WithdrawRequest();request.setAmount(new BigDecimal(amount));request.setLinkedBankAccountId(accountId);request.setPin("123456");return request;}
    private void authenticate(){var p=new UserPrincipal(owner);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
    private void error(ErrorCode expected,org.junit.jupiter.api.function.Executable operation){assertEquals(expected,assertThrows(AppException.class,operation).getErrorCode());}
}
