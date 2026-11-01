package com.example.demo.wallet.config;

import java.math.BigDecimal;
import java.time.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated
@ConfigurationProperties("wallet.limits")
public class WalletLimitProperties {
    @NotBlank private String zone = "Asia/Ho_Chi_Minh";
    @NotNull private Duration requestWindow = Duration.ofMinutes(10);
    @Min(1) @Max(1000) private int maxRequests = 10;
    @Valid @NotNull private Limit topUp = new Limit("50000000", "100000000");
    @Valid @NotNull private Limit withdraw = new Limit("20000000", "50000000");
    @Valid @NotNull private Limit payment = new Limit("50000000", "100000000");

    @Getter @Setter
    public static class Limit {
        @NotNull @DecimalMin("0.0001") @Digits(integer=15,fraction=4)
        private BigDecimal perTransaction;
        @NotNull @DecimalMin("0.0001") @Digits(integer=15,fraction=4)
        private BigDecimal perDay;
        public Limit(String transaction, String day) {
            perTransaction = new BigDecimal(transaction); perDay = new BigDecimal(day);
        }
        @AssertTrue(message="Daily limit must cover the per-transaction limit")
        public boolean isRangeValid() {
            return perTransaction == null || perDay == null || perDay.compareTo(perTransaction) >= 0;
        }
    }
}
