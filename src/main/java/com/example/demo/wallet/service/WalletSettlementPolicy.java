package com.example.demo.wallet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.example.demo.ecommerce.entity.Order;
import com.example.demo.ecommerce.entity.OrderItem;

@Component
public class WalletSettlementPolicy {
    private final BigDecimal feePercent;
    private final BigDecimal cashbackPercent;
    private final Duration returnWindow;

    public WalletSettlementPolicy(@Value("${wallet.settlement.fee-percent:0}") BigDecimal feePercent,
            @Value("${wallet.cashback.percent:0}") BigDecimal cashbackPercent,
            @Value("${wallet.settlement.return-window:P7D}") Duration returnWindow) {
        if (feePercent.signum() < 0 || feePercent.compareTo(new BigDecimal("100")) > 0
                || cashbackPercent.signum() < 0 || cashbackPercent.compareTo(new BigDecimal("100")) > 0
                || cashbackPercent.scale() > 4 || feePercent.scale() > 4
                || returnWindow.isNegative() || returnWindow.isZero()) throw new IllegalArgumentException("Invalid wallet settlement policy");
        this.feePercent = feePercent; this.cashbackPercent = cashbackPercent; this.returnWindow = returnWindow;
    }

    /** Freeze policy and proportional line allocations at checkout, never use current product ownership later. */
    public void snapshot(Order order) {
        order.setFeePercent(feePercent);
        order.setCashbackPercent(cashbackPercent);
        order.setReturnWindowSeconds(returnWindow.toSeconds());
        var items = order.getItems().stream().filter(item -> gross(item).signum() > 0).toList();
        BigDecimal weight = items.stream().map(this::gross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = order.getFinalAmount();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            BigDecimal share = i == items.size() - 1 ? remaining
                    : order.getFinalAmount().multiply(gross(item)).divide(weight, 4, RoundingMode.DOWN);
            remaining = remaining.subtract(share);
            item.setSettlementAmount(share);
            item.setPlatformFee(share.multiply(feePercent).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }
    }

    private BigDecimal gross(OrderItem item) { return item.getPriceAtPurchase().multiply(BigDecimal.valueOf(item.getQuantity())); }
}
