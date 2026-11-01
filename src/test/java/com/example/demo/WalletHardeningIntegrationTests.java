package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.support.IsolatedMySqlDatabase;
import com.example.demo.user.api.UserRole;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.security.UserPrincipal;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.dto.*;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.gateway.BankGateway;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.service.*;

/** Regression coverage for concurrent bank revocation and scheduler isolation. */
@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN","logging.level.org.hibernate=WARN"})
@ContextConfiguration(initializers=IsolatedMySqlDatabase.class)
@Import(WalletHardeningIntegrationTests.ProbeConfig.class)
class WalletHardeningIntegrationTests {
    static final class RouteGate {
        volatile Long transferId;
        final CountDownLatch read = new CountDownLatch(1), resume = new CountDownLatch(1);
    }
    static final class ProbeBank implements BankGateway {
        final AtomicInteger submissions = new AtomicInteger();
        public String provider(){return "REVIEW_PROBE";}
        public VerifiedAccount verifyOwnership(Long id,String proof){return new VerifiedAccount("account:"+id,"TEST","Review bank","****1234","Review owner");}
        public Result resolve(Command command){
            submissions.incrementAndGet();
            return new Result(command.merchantReference(),command.amount(),command.currency(),command.accountToken(),
                    "bank:"+command.merchantReference(),State.SUCCEEDED,null,null);
        }
    }
    @TestConfiguration static class ProbeConfig {
        @Bean ProbeBank probeBank(){return new ProbeBank();}
        @Bean static BeanPostProcessor pauseBankRoute(){
            return new BeanPostProcessor(){
                @Override public Object postProcessAfterInitialization(Object bean,String name){
                    if(!"bankTransferRepository".equals(name))return bean;
                    ProxyFactory wrapper=new ProxyFactory(bean);
                    wrapper.addAdvice((MethodInterceptor) invocation->{
                        Object result=invocation.proceed();
                        if(invocation.getMethod().getName().equals("findRoute")
                                &&Thread.currentThread().getName().equals("wallet-review-bank")
                                &&invocation.getArguments()[0].equals(GATE.transferId)){
                            GATE.read.countDown();
                            if(!GATE.resume.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Probe route timeout");
                        }
                        return result;
                    });
                    return wrapper.getProxy();
                }
            };
        }
    }
    static final RouteGate GATE=new RouteGate();
    @Autowired UserRepository users;
    @Autowired WalletRepository wallets;
    @Autowired WalletSecurityRepository security;
    @Autowired WalletService walletService;
    @Autowired WalletFundingService funding;
    @Autowired WalletBankManagementService management;
    @Autowired WalletBankWorker worker;
    @Autowired BankTransferRepository transfers;
    @Autowired WalletTransactionRepository transactions;
    @Autowired LinkedBankAccountRepository accounts;
    @Autowired ProbeBank bank;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @Autowired ApplicationContext context;

    @Test void revocationCommittedAfterRouteReadSuspendsUnsentWithdrawal()throws Exception{
        String name="review_"+UUID.randomUUID().toString().replace("-","");
        User owner=users.saveAndFlush(User.builder().username(name).email(name+"@example.test")
                .password(encoder.encode("password123")).role(UserRole.CUSTOMER).build());
        var principal=new UserPrincipal(owner);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        try{
            Long walletId=walletService.initializeWallet(owner.getId()).getId();
            new TransactionTemplate(manager).executeWithoutResult(tx->{
                var wallet=wallets.findByIdForUpdate(walletId).orElseThrow();wallet.setBalance(new BigDecimal("100"));
                security.saveAndFlush(WalletSecurity.builder().wallet(wallet).pinHash(encoder.encode("123456")).build());
            });
            var link=new LinkBankAccountRequest();link.setVerificationToken("probe");
            Long accountId=funding.linkAccount(link).getLinkedBankAccountId();
            var request=new WithdrawRequest();request.setAmount(new BigDecimal("20"));request.setLinkedBankAccountId(accountId);request.setPin("123456");
            Long transactionId=funding.withdraw(request,"review-withdrawal").getTransactionId();
            GATE.transferId=transfers.findByTransactionId(transactionId).orElseThrow().getId();
            try(var executor=Executors.newSingleThreadExecutor(task->new Thread(task,"wallet-review-bank"))){
                Future<?> result=executor.submit(()->worker.process(GATE.transferId));
                try{
                    assertTrue(GATE.read.await(20,TimeUnit.SECONDS));
                    management.revoke(accountId,new WalletBankActionRequest("123456","Audit concurrent revocation"));
                    assertNotNull(accounts.findById(accountId).orElseThrow().getRevokedAt());
                }finally{GATE.resume.countDown();}
                result.get(20,TimeUnit.SECONDS);
            }
            assertEquals(0,bank.submissions.get());
            assertEquals(TransactionStatus.PENDING,transactions.findById(transactionId).orElseThrow().getStatus());
            assertTrue(transfers.findById(GATE.transferId).orElseThrow().isSuspended());
            assertNull(transfers.findById(GATE.transferId).orElseThrow().getDispatchStartedAt());
        }finally{GATE.resume.countDown();SecurityContextHolder.clearContext();}
    }

    @Test void maintenanceSchedulerHasMultipleThreads(){
        var scheduler=context.getBean("taskScheduler",ThreadPoolTaskScheduler.class);
        assertEquals(2,scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
    }

    @Test void stalledBankScheduleDoesNotBlockSettlementOrNotifications()throws Exception {
        var bankScheduler=context.getBean("walletBankScheduler",ThreadPoolTaskScheduler.class);
        var settlementScheduler=context.getBean("walletSettlementScheduler",ThreadPoolTaskScheduler.class);
        var notificationScheduler=context.getBean("walletNotificationScheduler",ThreadPoolTaskScheduler.class);
        assertNotSame(bankScheduler,settlementScheduler);assertNotSame(bankScheduler,notificationScheduler);
        CountDownLatch started=new CountDownLatch(1),resume=new CountDownLatch(1),others=new CountDownLatch(2);
        bankScheduler.execute(()->{started.countDown();try{resume.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        try {
            assertTrue(started.await(5,TimeUnit.SECONDS));
            settlementScheduler.execute(others::countDown);notificationScheduler.execute(others::countDown);
            assertTrue(others.await(5,TimeUnit.SECONDS));
        } finally { resume.countDown(); }
    }
}
