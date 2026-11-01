package com.example.demo.wallet.entity;
import com.example.demo.wallet.api.WalletBalanceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.*;
import jakarta.persistence.Table;
import lombok.*;
@Entity @Table(name="wallet_recovery_debts") @Check(constraints="amount > 0 and repaid_amount >= 0 and repaid_amount <= amount")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletRecoveryDebt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false,updatable=false) private Long userId;
    @Column(name="refund_case_id",nullable=false,updatable=false) private Long refundCaseId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="refund_case_id",insertable=false,updatable=false) @com.fasterxml.jackson.annotation.JsonIgnore private WalletRefundCase refundCase;
    @Column(nullable=false,length=3,updatable=false) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private WalletBalanceType balanceType;
    @Column(nullable=false,precision=19,scale=4,updatable=false) private BigDecimal amount;
    @Column(name="repaid_amount",nullable=false,precision=19,scale=4) @Builder.Default private BigDecimal repaidAmount=BigDecimal.ZERO;
    @CreationTimestamp @Column(updatable=false) private LocalDateTime createdAt;
    @Version private Long version;
}
