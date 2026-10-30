package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.util.HashSet;
import org.springframework.stereotype.Component;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class WalletPaymentPrerequisites {
    private final WalletRepository wallets;
    public void validate(Order order, String currency) {
        BigDecimal allocated = BigDecimal.ZERO;
        var sellerIds = new HashSet<Long>();
        for (var item : order.getItems()) {
            BigDecimal amount = item.getSettlementAmount();
            if (amount == null || item.getPlatformFee() == null || amount.signum() < 0
                    || item.getPlatformFee().signum() < 0 || item.getPlatformFee().compareTo(amount) > 0) fail();
            if (amount.signum() == 0) continue;
            if (item.getSellerId() == null) fail();
            if(item.getSellerId().equals(order.getUserId()))fail();
            sellerIds.add(item.getSellerId()); allocated = allocated.add(amount);
        }
        if (order.getFinalAmount() == null || allocated.compareTo(order.getFinalAmount()) != 0) fail();
        if (!sellerIds.isEmpty() && wallets.countByUserIdsAndCurrency(sellerIds, currency) != sellerIds.size()) fail();
    }
    private void fail() { throw new AppException(ErrorCode.WALLET_ORDER_NOT_READY); }
}
