package com.example.demo.wallet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.demo.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;

@Component @Slf4j
public class WalletReconciliationWorker {
    private final WalletRepository wallets;
    private final WalletReconciliationService reconciliation;
    private final boolean enabled;
    private final WalletAlertService alerts;
    public WalletReconciliationWorker(WalletRepository wallets, WalletReconciliationService reconciliation,
            WalletAlertService alerts,
            @Value("${wallet.reconciliation.worker-enabled:true}") boolean enabled) {
        this.wallets = wallets; this.reconciliation = reconciliation; this.enabled = enabled;
        this.alerts=alerts;
    }
    @Scheduled(fixedDelayString = "${wallet.reconciliation.poll-delay-ms:3600000}",
            initialDelayString = "${wallet.reconciliation.initial-delay-ms:3600000}")
    public void run() {
        if (!enabled) return;
        Long cursor = 0L;
        while (true) {
            var ids = wallets.findIdsAfter(cursor, PageRequest.of(0, 100));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try {
                    var report = reconciliation.inspect(id);
                    if (Boolean.FALSE.equals(report.getLedgerConsistent()) || !report.isHoldsConsistent()) {
                        alerts.record("WALLET_RECONCILIATION",id.toString(),"Số dư ví và sổ/khoản giữ không khớp");
                        log.error("Wallet {} reconciliation mismatch; review required", id);
                    }
                } catch (RuntimeException exception) {
                    log.warn("Wallet {} reconciliation unavailable ({})", id, exception.getClass().getSimpleName());
                }
                cursor = id;
            }
        }
    }
}
