package com.example.demo.wallet.api;
import com.example.demo.wallet.api.dto.WalletResponse;
public interface WalletAccountsApi {
    WalletResponse initializeWallet(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long userId);
}
