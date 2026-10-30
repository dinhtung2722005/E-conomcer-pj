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
    private long cursor;

    public WalletSettlementWorker(WalletSettlementService settlement, OrderRepository orders,
            @Value("${wallet.settlement.worker-enabled:true}") boolean enabled) {
        this.settlement = settlement; this.orders = orders; this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${wallet.settlement.poll-delay-ms:60000}",
            initialDelayString = "${wallet.settlement.initial-delay-ms:60000}")
    public synchronized void run() {
        if (!enabled) return;
        for (int batch = 0; batch < 10; batch++) {
            var ids = orders.findSettlementDueIds(LocalDateTime.now(), cursor, PageRequest.of(0, 100));
            if (ids.isEmpty()) { cursor = 0L; return; }
            for (Long id : ids) {
                try { settlement.settle(id); }
                catch (RuntimeException exception) {
                    log.warn("Settlement requires review for order {} ({})", id, exception.getClass().getSimpleName());
                }
                cursor = id;
            }
        }
    }
}
