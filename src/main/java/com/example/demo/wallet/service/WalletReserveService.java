package com.example.demo.wallet.service;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.api.WalletStatus;
import com.example.demo.wallet.api.WalletBalanceType;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.web.PageQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import com.example.demo.shared.error.*;
import com.example.demo.user.api.UserAccountsApi;
import com.example.demo.wallet.api.dto.WalletReserveFundingRequest;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
@Service @Validated @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class WalletReserveService {
    public record Reserve(String currency,BigDecimal balance){}
    public record Debt(Long id,Long userId,Long refundCaseId,WalletBalanceType source,String currency,BigDecimal amount,BigDecimal repaid){}
    private final WalletAdminAccess access;private final UserAccountsApi currentUser;private final WalletPinService pins;
    private final WalletRepository wallets;private final WalletDebtService debts;private final WalletRecoveryDebtRepository debtRows;
    private final WalletRefundReserveRepository reserves;private final WalletOperationService operations;
    private final WalletTransactionRepository transactions;private final WalletLedgerService ledger;private final WalletOpsAuditRepository audit;
    private final PlatformTransactionManager manager;
    @PreAuthorize("hasRole('ADMIN')") public Reserve balance(@Pattern(regexp="[A-Z]{3}") String currency){access.requireAdmin();return reserves.findById(currency).map(r->new Reserve(currency,r.getBalance())).orElse(new Reserve(currency,BigDecimal.ZERO));}
    @PreAuthorize("hasRole('ADMIN')") public Long fund(@Valid @NotNull WalletReserveFundingRequest request,String key){access.requireAdmin();return transfer(request,key,false);}
    public Long repay(@Valid @NotNull WalletReserveFundingRequest request,String key){return transfer(request,key,true);}
    public com.example.demo.shared.web.PageResponse<Debt> ownDebts(@Valid @NotNull com.example.demo.shared.web.PageQuery page){var user=currentUser.getCurrentUser();if(!user.isActive())throw new AppException(ErrorCode.USER_LOCKED);return com.example.demo.shared.web.PageResponse.from(debtRows.findAll((r,q,b)->b.equal(r.get("userId"),user.getId()),page.toPageable()).map(d->new Debt(d.getId(),d.getUserId(),d.getRefundCaseId(),d.getBalanceType(),d.getCurrency(),d.getAmount(),d.getRepaidAmount())));}
    @PreAuthorize("hasRole('ADMIN')") public com.example.demo.shared.web.PageResponse<Debt> list(@Valid @NotNull com.example.demo.shared.web.PageQuery page){access.requireAdmin();return com.example.demo.shared.web.PageResponse.from(debtRows.findAll(page.toPageable()).map(d->new Debt(d.getId(),d.getUserId(),d.getRefundCaseId(),d.getBalanceType(),d.getCurrency(),d.getAmount(),d.getRepaidAmount())));}
    private Long transfer(WalletReserveFundingRequest request,String key,boolean repayment){
        TransactionType type=repayment?TransactionType.DEBT_REPAYMENT:TransactionType.RESERVE_FUNDING;
        String fingerprint=operations.fingerprint(type+"|"+request.amount().stripTrailingZeros().toPlainString());var auth=pins.authorize(request.pin(),fingerprint);
        return new TransactionTemplate(manager).execute(tx->{
            pins.lockAuthorizingUser(auth);if(!repayment)access.requireAdmin();
            var wallet=wallets.findByUserIdForUpdate(currentUser.getCurrentUserId()).orElseThrow();pins.requireAuthorization(auth,wallet,fingerprint);
            var existing=operations.find(wallet,key,fingerprint);if(existing.isPresent())return transactions.findByOperationIdOrderByIdAsc(existing.get().getId()).getFirst().getId();
            if(wallet.getStatus()!=WalletStatus.ACTIVE)throw new AppException(ErrorCode.WALLET_LOCKED);
            if(wallet.getBalance().compareTo(request.amount())<0)throw new AppException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
            ledger.open(wallet);
            if(repayment){if(debts.collect(wallet,WalletBalanceType.CASH,request.amount()).compareTo(request.amount())!=0)throw new AppException(ErrorCode.INVALID_REQUEST);}
            else{var reserve=debts.reserve(wallet.getCurrency());reserve.setBalance(reserve.getBalance().add(request.amount()));}
            wallet.setBalance(wallet.getBalance().subtract(request.amount()));var op=operations.create(wallet,key,fingerprint,type,UUID.randomUUID().toString());
            var t=transactions.saveAndFlush(WalletTransaction.builder().wallet(wallet).operation(op).referenceId(op.getReference()).idempotencyKey("op:"+op.getId()+":CASH").type(type).amount(request.amount()).status(TransactionStatus.SUCCESS).completedAt(LocalDateTime.now()).build());
            ledger.move("reserve:"+t.getId(),wallet.getCurrency(),ledger.account(wallet,WalletBalanceType.CASH,false),"platform:REFUND_RESERVE",request.amount());
            audit.saveAndFlush(WalletOpsAudit.builder().actorId(currentUser.getCurrentUserId()).walletId(wallet.getId()).action(type.name()).reference(t.getId().toString()).reason(request.reason().strip()).build());
            return t.getId();
        });
    }
}
