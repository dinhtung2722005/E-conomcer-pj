package com.example.demo.ecommerce.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/** Trusted immutable financial snapshot, built by ecommerce while holding the order lock. */
@Getter
public final class OrderSnapshot {
    private final Long id,userId;
    private final OrderStatus status;
    private final BigDecimal finalAmount,refundedAmount,settledAmount,cashbackPercent;
    private final LocalDateTime createdAt,settlementAvailableAt,settledAt;
    private final List<Item> items;
    public OrderSnapshot(Long id,Long userId,OrderStatus status,BigDecimal finalAmount,BigDecimal refundedAmount,
            BigDecimal settledAmount,BigDecimal cashbackPercent,LocalDateTime createdAt,
            LocalDateTime settlementAvailableAt,LocalDateTime settledAt,List<Item> items){
        this.id=id;this.userId=userId;this.status=status;this.finalAmount=finalAmount;this.refundedAmount=refundedAmount;
        this.settledAmount=settledAmount;this.cashbackPercent=cashbackPercent;this.createdAt=createdAt;
        this.settlementAvailableAt=settlementAvailableAt;this.settledAt=settledAt;this.items=List.copyOf(items);
    }
    @Getter @lombok.AllArgsConstructor
    public static final class Item {
        private final Long id,productId,sellerId;
        private final Integer quantity;
        private final BigDecimal settlementAmount,platformFee;
    }
    public Item item(Long id){return items.stream().filter(i->i.getId().equals(id)).findFirst().orElseThrow();}
}
