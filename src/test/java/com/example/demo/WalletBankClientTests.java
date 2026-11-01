package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.example.demo.shared.error.AppException;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.gateway.*;

class WalletBankClientTests {
    @Test void timeoutBoundsWaitAndBusyWorkersCannotCreateUnboundedExternalCalls()throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        BankGateway gateway=new BankGateway(){
            public String provider(){return "TEST";}
            public VerifiedAccount verifyOwnership(Long id,String proof){throw new UnsupportedOperationException();}
            public Result resolve(Command command){
                started.countDown();
                // Model a partner client that ignores interrupts: the shared pool must remain bounded.
                boolean done=false;while(!done)try{release.await();done=true;}catch(InterruptedException ignored){}
                return null;
            }
        };
        try(var client=new WalletBankClient(1,Duration.ofMillis(100))){
            var command=new BankGateway.Command("test",TransactionType.WITHDRAW,BigDecimal.ONE,"VND","account");
            assertTimeoutPreemptively(Duration.ofSeconds(2),()->assertThrows(AppException.class,()->client.resolve(gateway,command,false)));
            assertTrue(started.await(1,TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1),()->assertThrows(AppException.class,()->client.resolve(gateway,command,false)));
        }finally{release.countDown();}
    }
}
