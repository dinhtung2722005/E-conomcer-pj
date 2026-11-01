package com.example.demo.wallet.api;

public enum WalletStatus {
    ACTIVE,
    /** Incoming deposits/refunds are allowed; new payments and withdrawals are blocked. */
    LOCKED
}
