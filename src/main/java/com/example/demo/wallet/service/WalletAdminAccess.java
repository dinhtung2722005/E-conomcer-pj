package com.example.demo.wallet.service;
import org.springframework.stereotype.Component;
import com.example.demo.user.entity.*;
import com.example.demo.user.exception.*;
import com.example.demo.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class WalletAdminAccess {
    private final CurrentUserService currentUser;
    public User requireAdmin() {
        User actor=currentUser.getCurrentUser();
        if(!actor.isActive()) throw new AppException(ErrorCode.USER_LOCKED);
        if(actor.getRole()!=UserRole.ADMIN) throw new AppException(ErrorCode.ACCESS_DENIED);
        return actor;
    }
}
