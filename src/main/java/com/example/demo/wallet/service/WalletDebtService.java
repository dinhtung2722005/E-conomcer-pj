package com.example.demo.wallet.service;
import com.example.demo.wallet.api.WalletBalanceType;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.MANDATORY)
public class WalletDebtService {
    private final WalletRecoveryDebtRepository debts; private final WalletRefundReserveRepository reserves;
    private final WalletTransactionRepository transactions;
    private final WalletLedgerService ledger;
    public WalletRefundReserve reserve(String currency){reserves.initialize(currency);return reserves.lock(currency).orElseThrow();}
    public void create(Long userId,Long refundCaseId,String currency,WalletBalanceType source,BigDecimal amount){
        if(amount.signum()>0)debts.saveAndFlush(WalletRecoveryDebt.builder().userId(userId).refundCaseId(refundCaseId).currency(currency).balanceType(source).amount(amount).build());
    }
    /** A successful deposit repays CASH debt before its remainder can be spent or withdrawn. */
    public void collectAvailableCash(Wallet wallet,String reference){
        if(wallet.getBalance().signum()==0||debts.outstanding(wallet.getUserId(),WalletBalanceType.CASH,wallet.getCurrency()).isEmpty())return;
        BigDecimal paid=collect(wallet,WalletBalanceType.CASH,wallet.getBalance());
        if(paid.signum()==0)return;
        wallet.setBalance(wallet.getBalance().subtract(paid));
        var transaction=transactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).referenceId(reference)
            .idempotencyKey("debt:"+reference).type(com.example.demo.wallet.api.TransactionType.DEBT_REPAYMENT)
            .amount(paid).status(com.example.demo.wallet.api.TransactionStatus.SUCCESS).completedAt(java.time.LocalDateTime.now()).build());
        ledger.move("debt:"+transaction.getId(),wallet.getCurrency(),ledger.account(wallet,WalletBalanceType.CASH,false),"platform:REFUND_RESERVE",paid);
    }
    /** Caller locks the owner's wallet first. CASH repayments restore the actually prefunded reserve. */
    public BigDecimal collect(Wallet wallet,WalletBalanceType type,BigDecimal maximum){
        var reserve=type==WalletBalanceType.CASH?reserve(wallet.getCurrency()):null;
        BigDecimal remaining=maximum;
        for(var debt:debts.outstanding(wallet.getUserId(),type,wallet.getCurrency())){
            BigDecimal take=remaining.min(debt.getAmount().subtract(debt.getRepaidAmount()));
            debt.setRepaidAmount(debt.getRepaidAmount().add(take));remaining=remaining.subtract(take);if(remaining.signum()==0)break;
        }
        BigDecimal paid=maximum.subtract(remaining);if(reserve!=null)reserve.setBalance(reserve.getBalance().add(paid));return paid;
    }
}
