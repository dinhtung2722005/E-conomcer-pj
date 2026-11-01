package com.example.demo.wallet.gateway;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.example.demo.shared.error.*;
import com.example.demo.shared.logging.LogContext;
import jakarta.annotation.PreDestroy;

/** Bounds both time and outstanding external calls. A timeout is always an unknown money outcome. */
@Component
public class WalletBankClient implements AutoCloseable {
    private final ExecutorService calls;
    private final long timeoutMillis;
    public WalletBankClient(@Value("${wallet.bank.max-concurrent-calls:4}") int concurrency,
            @Value("${wallet.bank.call-timeout:PT10S}") Duration timeout) {
        if(concurrency<1||concurrency>32||timeout.isNegative()||timeout.toMillis()<1||timeout.compareTo(Duration.ofSeconds(60))>0)
            throw new IllegalArgumentException("Invalid bank concurrency/timeout policy");
        timeoutMillis=timeout.toMillis();
        var sequence=new AtomicInteger();
        calls=new ThreadPoolExecutor(concurrency,concurrency,30,TimeUnit.SECONDS,new SynchronousQueue<>(),task->{
            Thread thread=new Thread(task,"wallet-bank-io-"+sequence.incrementAndGet()); thread.setDaemon(true); return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public BankGateway.VerifiedAccount verifyOwnership(BankGateway gateway,Long userId,String proof) {
        return call(()->gateway.verifyOwnership(userId,proof));
    }
    public BankGateway.Result resolve(BankGateway gateway,BankGateway.Command command,boolean lookupOnly) {
        return call(()->lookupOnly?gateway.lookup(command):gateway.resolve(command));
    }
    private <T> T call(Callable<T> operation) {
        Future<T> pending;
        var context=org.slf4j.MDC.getCopyOfContextMap();
        try {
            pending=calls.submit(()->{try(var scope=new LogContext(context==null?java.util.Map.of():context)){return operation.call();}});
        } catch(RejectedExecutionException exception) { throw new AppException(ErrorCode.WALLET_BANK_UNAVAILABLE); }
        try { return pending.get(timeoutMillis,TimeUnit.MILLISECONDS); }
        catch(InterruptedException exception) { pending.cancel(true);Thread.currentThread().interrupt();throw new AppException(ErrorCode.WALLET_BANK_UNAVAILABLE); }
        catch(TimeoutException exception) { pending.cancel(true);throw new AppException(ErrorCode.WALLET_BANK_UNAVAILABLE); }
        catch(ExecutionException exception) {
            if(exception.getCause() instanceof AppException domain)throw domain;
            throw new AppException(ErrorCode.WALLET_BANK_UNAVAILABLE);
        }
    }
    @Override @PreDestroy public void close() { calls.shutdownNow(); }
}
