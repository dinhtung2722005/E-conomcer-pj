package com.example.demo.wallet.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="wallet_job_cursors") @Getter @Setter @NoArgsConstructor
public class WalletJobCursor {
    @Id @Column(length=50) private String name;
    @Column(nullable=false) private long lastId;
    @Version private Long version;
}
