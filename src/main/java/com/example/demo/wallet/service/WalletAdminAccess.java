package com.example.demo.wallet.service;
import com.example.demo.user.api.UserAccount;
import com.example.demo.user.api.UserRole;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import org.springframework.stereotype.Component;
import com.example.demo.shared.error.*;
import com.example.demo.user.api.UserAccountsApi;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class WalletAdminAccess {
    private final UserAccountsApi currentUser;
    public UserAccount requireAdmin() {
        UserAccount actor=currentUser.getCurrentUser();
        if(!actor.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        if(actor.getRole()!=UserRole.ADMIN) throw new AppException(ErrorCode.ACCESS_DENIED);
        return actor;
    }
}
