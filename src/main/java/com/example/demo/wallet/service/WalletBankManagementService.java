package com.example.demo.wallet.service;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.wallet.api.TransactionType;
import com.example.demo.wallet.api.WalletBalanceType;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import com.example.demo.shared.error.*;
import com.example.demo.user.api.UserAccountsApi;
import com.example.demo.wallet.api.dto.WalletBankActionRequest;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
@Service @Validated @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class WalletBankManagementService {
    private final WalletPinService pins; private final UserAccountsApi currentUser;
    private final WalletRepository wallets; private final LinkedBankAccountRepository accounts;
    private final BankTransferRepository transfers; private final WalletTransactionRepository transactions;
    private final WalletHoldRepository holds; private final WalletLedgerService ledger;
    private final WalletOpsAuditRepository audit; private final PlatformTransactionManager manager;
    private final WalletNotificationService notifications;
    public void revoke(@NotNull @Positive Long id,@NotNull @Valid WalletBankActionRequest request){
        var auth=pins.authorize(request.pin(),"REVOKE_BANK:"+id);
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            pins.lockAuthorizingUser(auth);var wallet=wallets.findByUserIdForUpdate(currentUser.getCurrentUserId()).orElseThrow();
            pins.requireAuthorization(auth,wallet,"REVOKE_BANK:"+id);
            var account=accounts.findByIdAndWalletId(id,wallet.getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_BANK_ACCOUNT_NOT_FOUND));
            if(account.getRevokedAt()!=null)return;account.setRevokedAt(LocalDateTime.now());
            audit.saveAndFlush(WalletOpsAudit.builder().actorId(currentUser.getCurrentUserId()).walletId(wallet.getId()).action("REVOKE_BANK").reference(id.toString()).reason(request.reason().strip()).build());
            notifications.publish(wallet,"bank-revoked:"+id,"Thu hồi liên kết ngân hàng","Liên kết ngân hàng đã được thu hồi; yêu cầu mới không thể dùng liên kết này.",null);
        });
    }
    public void cancelWithdrawal(@NotNull @Positive Long transactionId,@NotNull @Valid WalletBankActionRequest request){
        var auth=pins.authorize(request.pin(),"CANCEL_WITHDRAWAL:"+transactionId);
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            pins.lockAuthorizingUser(auth);var wallet=wallets.findByUserIdForUpdate(currentUser.getCurrentUserId()).orElseThrow();
            pins.requireAuthorization(auth,wallet,"CANCEL_WITHDRAWAL:"+transactionId);
            var transaction=transactions.findByIdAndWalletIdForUpdate(transactionId,wallet.getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_TRANSACTION_NOT_FOUND));
            if(transaction.getType()!=TransactionType.WITHDRAW)throw new AppException(ErrorCode.WALLET_TRANSACTION_NOT_FOUND);
            if(transaction.getStatus()==TransactionStatus.CANCELLED)return;
            var hold=holds.findByIdAndWalletIdForUpdate(transaction.getHold().getId(),wallet.getId()).orElseThrow();
            var transferId=transfers.findByTransactionId(transactionId).orElseThrow().getId();
            var transfer=transfers.findByIdForUpdate(transferId).orElseThrow();
            if(transfer.isFinished()||transfer.getDispatchStartedAt()!=null||transfer.getAttempts()!=0)throw new AppException(ErrorCode.WALLET_TRANSFER_ALREADY_DISPATCHED);
            ledger.open(wallet);hold.release(transaction.getAmount());
            wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));wallet.setPendingBalance(wallet.getPendingBalance().subtract(transaction.getAmount()));
            ledger.move("withdraw-cancel:"+transactionId,wallet.getCurrency(),ledger.account(wallet,WalletBalanceType.CASH,true),ledger.account(wallet,WalletBalanceType.CASH,false),transaction.getAmount());
            transaction.setStatus(TransactionStatus.CANCELLED);transaction.setCompletedAt(LocalDateTime.now());
            transfer.setFinished(true);transfer.setSuspended(false);transfer.setLastError("USER_CANCELLED");
            audit.saveAndFlush(WalletOpsAudit.builder().actorId(currentUser.getCurrentUserId()).walletId(wallet.getId()).action("CANCEL_WITHDRAWAL").reference(transactionId.toString()).reason(request.reason().strip()).build());
        });
    }
}
