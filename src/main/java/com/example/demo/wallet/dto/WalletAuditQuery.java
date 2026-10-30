package com.example.demo.wallet.dto;

import java.time.LocalDateTime;
import com.example.demo.user.dto_request.PageQuery;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class WalletAuditQuery extends PageQuery {
    @Positive private Long actorId;
    @Positive private Long walletId;
    @Pattern(regexp = "[A-Z_]{1,50}") private String action;
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,100}") private String reference;
    @Pattern(regexp = "[A-Za-z0-9_-]{8,80}") private String requestId;
    @Pattern(regexp = "SUCCESS|REJECTED") private String outcome;
    private LocalDateTime from;
    private LocalDateTime to;
    @AssertTrue(message = "from phải trước to") @JsonIgnore
    public boolean isRangeValid() { return from == null || to == null || from.isBefore(to); }
}
