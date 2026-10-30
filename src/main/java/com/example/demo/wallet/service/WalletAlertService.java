package com.example.demo.wallet.service;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.wallet.repository.WalletAlertRepository;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class WalletAlertService {
    private final WalletAlertRepository alerts;
    @Transactional
    public void record(String category,String reference,String message) {
        alerts.record(category+":"+reference,category,reference,message,LocalDateTime.now());
    }
}
