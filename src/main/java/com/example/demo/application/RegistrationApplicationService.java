package com.example.demo.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.example.demo.user.api.*;
import com.example.demo.user.api.dto.*;
import com.example.demo.wallet.api.WalletAccountsApi;
import lombok.RequiredArgsConstructor;

@Service @Validated @RequiredArgsConstructor
public class RegistrationApplicationService implements RegistrationUseCase {
    private final UserRegistrationApi users;
    private final WalletAccountsApi wallets;
    @Override @Transactional public UserProfileResponse register(UserCreationRequest request) {
        var account=users.registerAccount(request);
        wallets.initializeWallet(account.getId());
        return account;
    }
    @Override @Transactional public UserProfileResponse createUser(UserCreationRequest request) {
        var account=users.createAccount(request);
        wallets.initializeWallet(account.getId());
        return account;
    }
}
