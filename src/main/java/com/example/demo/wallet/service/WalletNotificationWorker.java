package com.example.demo.wallet.service;
import java.util.EnumSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class WalletNotificationWorker {
    private final WalletNotificationService notifications;private final WalletJobCursorService cursors;
    private final WalletTransactionRepository transactions;
    @Value("${wallet.notifications.worker-enabled:true}") private boolean enabled;
    @Scheduled(scheduler="walletNotificationScheduler",fixedDelayString="${wallet.notifications.poll-delay-ms:10000}",initialDelayString="${wallet.notifications.initial-delay-ms:10000}")
    public void run(){if(!enabled)return;for(int batch=0;batch<10;batch++){
        var ids=cursors.claim("notifications",(after,page)->transactions.notificationIds(EnumSet.of(TransactionStatus.SUCCESS,TransactionStatus.FAILED,TransactionStatus.CANCELLED),after,page));
        if(ids.isEmpty())return;for(Long id:ids){try{notifications.publishTransaction(id);}catch(RuntimeException ex){log.warn("Notification pending for transaction {}",id);}}
    }}
}
