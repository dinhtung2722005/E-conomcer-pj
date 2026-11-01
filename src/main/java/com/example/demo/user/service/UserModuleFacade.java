package com.example.demo.user.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.demo.user.api.*;
import com.example.demo.user.api.dto.*;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.shared.error.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class UserModuleFacade implements UserAccountsApi, UserRegistrationApi {
    private final UserRepository users;
    private final CurrentUserService current;
    private final SessionRevocationService sessions;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final UserService userService;

    @Override public Long getCurrentUserId() { return current.getCurrentUserId(); }
    @Override public UserAccount getCurrentPrincipal() {
        var user=current.getCurrentPrincipal();
        return new UserAccount(user.getId(),user.getEmail(),user.getRole(),user.isActive(),user.getTokenVersion());
    }
    @Override @Transactional(readOnly=true) public UserAccount getCurrentUser() { return snapshot(current.getCurrentUser()); }
    @Override @Transactional(readOnly=true) public Optional<UserAccount> findById(Long id) { return users.findById(id).map(this::snapshot); }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Optional<UserAccount> findByIdForUpdate(Long id) { return users.findByIdForUpdate(id).map(this::snapshot); }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public UserAccount requireCurrent(boolean administrator) {
        var user=users.findByIdForUpdate(current.getCurrentUserId()).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));
        if(!user.isActive())throw new AppException(ErrorCode.USER_LOCKED);
        if(user.getTokenVersion()!=current.getCurrentPrincipal().getTokenVersion())throw new AppException(ErrorCode.TOKEN_INVALID);
        if(administrator&&user.getRole()!=UserRole.ADMIN)throw new AppException(ErrorCode.ACCESS_DENIED);
        return snapshot(user);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public boolean matchesPassword(Long id,String password) {
        var user=users.findByIdForUpdate(id).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));
        return encoder.matches(password,user.getPassword());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY) public void revokeAllSessions(Long id) { sessions.revokeAllSessions(id); }
    @Override @Transactional(propagation=Propagation.MANDATORY) public UserProfileResponse registerAccount(UserCreationRequest request) { return auth.createAccount(request); }
    @Override @Transactional(propagation=Propagation.MANDATORY) public UserProfileResponse createAccount(UserCreationRequest request) { return userService.createAccount(request); }
    private UserAccount snapshot(User user) { return new UserAccount(user.getId(),user.getEmail(),user.getRole(),user.isActive(),user.getTokenVersion()); }
}
