package com.example.demo.wallet.entity;

public enum WalletStatus {
    ACTIVE,
    /** Incoming deposits/refunds are allowed; new payments and withdrawals are blocked. */
    LOCKED
}
