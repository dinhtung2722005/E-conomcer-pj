package com.example.demo.wallet.service;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class WalletOpsSlaWorker {
    private final BankTransferRepository transfers;
    private final WalletRefundCaseRepository refunds;
    private final WalletJournalRepository journals;
    private final WalletJobCursorService cursors;
    private final WalletAlertService alerts;
    @Value("${wallet.ops.worker-enabled:true}") private boolean enabled;
    @Value("${wallet.ops.transfer-sla:PT48H}") private Duration transferSla;
    @Value("${wallet.ops.refund-sla:PT48H}") private Duration refundSla;
    @Scheduled(fixedDelayString="${wallet.ops.poll-delay-ms:60000}",initialDelayString="${wallet.ops.initial-delay-ms:60000}")
    public void run() {
        if(!enabled)return;
        for(int batch=0;batch<10;batch++) {
            var ids=cursors.claim("sla-bank",(after,page)->transfers.overdueIds(LocalDateTime.now().minus(transferSla),after,page));
            ids.forEach(id->alerts.record("TRANSFER_SLA",id.toString(),"Giao dịch ngân hàng quá thời hạn xử lý"));
            if(ids.isEmpty())break;
        }
        for(int batch=0;batch<10;batch++) {
            var ids=cursors.claim("sla-refund",(after,page)->refunds.overdueIds(LocalDateTime.now().minus(refundSla),after,page));
            ids.forEach(id->alerts.record("REFUND_SLA",id.toString(),"Hồ sơ hoàn tiền quá thời hạn xử lý"));
            if(ids.isEmpty())break;
        }
        if(journals.countUnbalancedJournals()>0)alerts.record("LEDGER","GLOBAL","Có bút toán không cân bằng, cần kiểm tra");
    }
}
