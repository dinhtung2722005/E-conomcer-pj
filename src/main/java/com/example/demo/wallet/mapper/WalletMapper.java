package com.example.demo.wallet.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.example.demo.wallet.dto.WalletHoldResponse;
import com.example.demo.wallet.dto.WalletResponse;
import com.example.demo.wallet.dto.WalletSecurityResponse;
import com.example.demo.wallet.dto.WalletTransactionResponse;
import com.example.demo.wallet.entity.TransactionStatus;
import com.example.demo.wallet.entity.Wallet;
import com.example.demo.wallet.entity.WalletHold;
import com.example.demo.wallet.entity.WalletSecurity;
import com.example.demo.wallet.entity.WalletTransaction;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, imports = TransactionStatus.class)
public interface WalletMapper {
    @Mapping(target = "pinConfigured", source = "pinConfigured")
    WalletResponse toWalletResponse(Wallet wallet, boolean pinConfigured);

    @Mapping(target = "originalTransactionId", source = "originalTransaction.id")
    @Mapping(target = "failureReason", expression =
            "java(transaction.getStatus() == TransactionStatus.FAILED ? \"Giao dịch không thành công\" : null)")
    WalletTransactionResponse toTransactionResponse(WalletTransaction transaction);

    @Mapping(target = "orderId", source = "order.id")
    WalletHoldResponse toHoldResponse(WalletHold hold);

    @Mapping(target = "pinConfigured", expression =
            "java(security.getPinHash() != null && !security.getPinHash().isBlank())")
    WalletSecurityResponse toSecurityResponse(WalletSecurity security);
}
