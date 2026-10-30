package com.example.demo.wallet.entity;
import java.math.BigDecimal;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import lombok.*;
@Entity @Table(name="wallet_refund_reserves") @Check(constraints="balance >= 0") @Getter @Setter @NoArgsConstructor
public class WalletRefundReserve {
    @Id @Column(length=3) private String currency;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal balance;
    @Version private Long version;
}
