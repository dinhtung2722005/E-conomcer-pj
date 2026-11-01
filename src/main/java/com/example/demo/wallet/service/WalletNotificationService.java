package com.example.demo.wallet.service;
import com.example.demo.wallet.api.TransactionStatus;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.web.PageResponse;
import com.example.demo.shared.web.PageQuery;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.example.demo.user.api.dto.*;
import com.example.demo.shared.web.*;
import com.example.demo.shared.error.*;
import com.example.demo.user.api.UserAccountsApi;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
@Service @Validated @RequiredArgsConstructor
public class WalletNotificationService {
    public record Response(Long id,String title,String message,Long transactionId,LocalDateTime createdAt,LocalDateTime readAt){}
    private final WalletNotificationRepository notifications;private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;private final UserAccountsApi currentUser;
    @Transactional public void publishTransaction(Long id){
        Long walletId=transactions.findWalletId(id).orElseThrow();var wallet=wallets.findByIdForUpdate(walletId).orElseThrow();
        var t=transactions.findByIdAndWalletIdForUpdate(id,walletId).orElseThrow();if(t.isNotificationPublished())return;
        if(t.getStatus()!=TransactionStatus.SUCCESS&&t.getStatus()!=TransactionStatus.FAILED&&t.getStatus()!=TransactionStatus.CANCELLED)return;
        publish(wallet,"transaction:"+id,"Cập nhật giao dịch ví",t.getType()+" · "+t.getAmount().toPlainString()+" "+wallet.getCurrency()+" · "+t.getStatus(),id);
        t.setNotificationPublished(true);
    }
    @Transactional(propagation=Propagation.MANDATORY) public void publish(Wallet wallet,String key,String title,String message,Long transactionId){
        notifications.saveAndFlush(WalletNotification.builder().wallet(wallet).eventKey(key).title(title).message(message).transactionId(transactionId).build());
    }
    @Transactional(readOnly=true) public PageResponse<Response> list(@NotNull @Valid PageQuery page){
        return PageResponse.from(notifications.findByWalletIdOrderByIdDesc(ownWallet().getId(),page.toPageable()).map(this::response));
    }
    @Transactional public Response markRead(@NotNull @Positive Long id){
        var row=notifications.findByIdAndWalletId(id,ownWallet().getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_NOTIFICATION_NOT_FOUND));
        if(row.getReadAt()==null)row.setReadAt(LocalDateTime.now());return response(row);
    }
    private Wallet ownWallet(){var user=currentUser.getCurrentUser();if(!user.isActive())throw new AppException(ErrorCode.USER_LOCKED);return wallets.findByUserId(user.getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_NOT_FOUND));}
    private Response response(WalletNotification n){return new Response(n.getId(),n.getTitle(),n.getMessage(),n.getTransactionId(),n.getCreatedAt(),n.getReadAt());}
}
