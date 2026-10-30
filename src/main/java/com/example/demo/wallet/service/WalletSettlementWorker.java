package com.example.demo.wallet.service;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.demo.ecommerce.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;

@Component @Slf4j
public class WalletSettlementWorker {
    private final WalletSettlementService settlement;
    private final OrderRepository orders;
    private final boolean enabled;
    private final WalletJobCursorService cursors;
    private final WalletAlertService alerts;
    private final WalletSettlementRetryService retries;

    public WalletSettlementWorker(WalletSettlementService settlement, OrderRepository orders,
            WalletJobCursorService cursors, WalletAlertService alerts, WalletSettlementRetryService retries,
            @Value("${wallet.settlement.worker-enabled:true}") boolean enabled) {
        this.settlement = settlement; this.orders = orders; this.enabled = enabled;
        this.cursors=cursors; this.alerts=alerts; this.retries=retries;
    }

    @Scheduled(fixedDelayString = "${wallet.settlement.poll-delay-ms:60000}",
            initialDelayString = "${wallet.settlement.initial-delay-ms:60000}")
    public synchronized void run() {
        if (!enabled) return;
        for (int batch = 0; batch < 10; batch++) {
            var ids = cursors.claim("settlement",(after,page)->orders.findSettlementDueIds(LocalDateTime.now(),after,page));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try { settlement.settle(id); }
                catch (RuntimeException exception) {
                    retries.failed(id);
                    alerts.record("SETTLEMENT",id.toString(),"Quyết toán cần kiểm tra dữ liệu");
                    log.warn("Settlement requires review for order {} ({})", id, exception.getClass().getSimpleName());
                }
            }
        }
    }
}
