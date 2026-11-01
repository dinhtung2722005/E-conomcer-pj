package com.example.demo.wallet.api;

public enum TransactionStatus {
    PENDING,
    /** Submitted for processing; an uncertain external result must remain nonterminal. */
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED
}
