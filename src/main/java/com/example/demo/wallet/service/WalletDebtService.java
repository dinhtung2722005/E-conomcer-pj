package com.example.demo.wallet.service;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.MANDATORY)
public class WalletDebtService {
    private final WalletRecoveryDebtRepository debts; private final WalletRefundReserveRepository reserves;
    public WalletRefundReserve reserve(String currency){reserves.initialize(currency);return reserves.lock(currency).orElseThrow();}
    public void create(Long userId,Long refundCaseId,String currency,WalletBalanceType source,BigDecimal amount){
        if(amount.signum()>0)debts.saveAndFlush(WalletRecoveryDebt.builder().userId(userId).refundCaseId(refundCaseId).currency(currency).balanceType(source).amount(amount).build());
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
