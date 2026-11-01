package com.example.demo.wallet.api;
public interface SecurityEventsApi {
    void rejection(Long actorId,Long walletId,SecurityAction action,String code,String reference);
}
