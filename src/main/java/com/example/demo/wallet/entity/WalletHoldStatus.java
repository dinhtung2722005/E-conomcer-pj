package com.example.demo.wallet.entity;

public enum WalletHoldStatus {
    ACTIVE,
    PARTIALLY_PROCESSED,
    CAPTURED,
    RELEASED,
    /** Entire hold consumed by a mixture of capture and release. */
    CLOSED
}
