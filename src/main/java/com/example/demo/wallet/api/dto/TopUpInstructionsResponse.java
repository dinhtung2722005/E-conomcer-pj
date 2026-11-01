package com.example.demo.wallet.api.dto;

import lombok.Getter;
import lombok.Setter;

/** Normalized receiving-account instructions supplied by the gateway; not a user's withdrawal destination. */
@Getter
@Setter
public class TopUpInstructionsResponse {
    private String bankCode;
    private String bankName;
    private String accountNumber;
    private String accountHolderName;
    private String transferContent;
    private String qrCodePayload;
}
