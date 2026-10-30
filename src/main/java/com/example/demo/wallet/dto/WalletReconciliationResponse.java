package com.example.demo.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class WalletReconciliationResponse {
    private Long walletId;
    private boolean ledgerInitialized;
    /** Null until an opening snapshot exists; an uninitialized ledger is not reported as matching. */
    private Boolean ledgerConsistent;
    private boolean holdsConsistent;
    private Map<String, BigDecimal> ledgerDifferences;
    private BigDecimal cashHeldDifference;
    private BigDecimal bonusHeldDifference;
    private LocalDateTime checkedAt;
}
