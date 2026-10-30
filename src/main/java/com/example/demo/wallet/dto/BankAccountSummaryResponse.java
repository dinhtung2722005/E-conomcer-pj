package com.example.demo.wallet.dto;

import lombok.Getter;
import lombok.Setter;

/** Display data only. The funding service must mask the account number before constructing this response. */
@Getter
@Setter
public class BankAccountSummaryResponse {
    private boolean active;
    private Long linkedBankAccountId;
    private String bankCode;
    private String bankName;
    private String maskedAccountNumber;
    private String accountHolderName;
}
