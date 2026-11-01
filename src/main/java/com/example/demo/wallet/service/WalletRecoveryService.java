package com.example.demo.wallet.service;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.shared.error.AppException;
import com.example.demo.wallet.api.dto.WalletRecoveryDtos;
import java.security.SecureRandom;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import com.example.demo.user.api.UserAccount;
import com.example.demo.shared.error.*;
import com.example.demo.user.api.UserAccountsApi;
import com.example.demo.user.api.*;
import com.example.demo.wallet.api.dto.WalletRecoveryDtos.*;
import com.example.demo.wallet.entity.*;
import com.example.demo.wallet.api.SecurityAction;
import com.example.demo.wallet.api.SecurityOutcome;
import com.example.demo.wallet.repository.*;
import com.example.demo.wallet.gateway.WalletRecoveryDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Service @Validated @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class WalletRecoveryService {
    private final UserAccountsApi users; private final WalletRepository wallets; private final WalletSecurityRepository security;
    private final WalletPinRecoveryRepository challenges; private final UserAccountsApi currentUser;
    private final PasswordEncoder encoder; private final ObjectProvider<WalletRecoveryDelivery> delivery;
    private final WalletPinService pins; private final UserAccountsApi sessions;
    private final WalletNotificationService notifications;
    private final WalletSecurityEventService events;
    private final TransactionTemplate database; private final Duration withdrawalCooldown;
    private final SecureRandom random=new SecureRandom();
    public WalletRecoveryService(UserAccountsApi users,WalletRepository wallets,WalletSecurityRepository security,
            WalletPinRecoveryRepository challenges,UserAccountsApi currentUser,PasswordEncoder encoder,
            ObjectProvider<WalletRecoveryDelivery> delivery,WalletPinService pins,UserAccountsApi sessions,
            WalletNotificationService notifications, WalletSecurityEventService events,
            PlatformTransactionManager manager,@Value("${wallet.recovery.withdrawal-cooldown:PT24H}") Duration withdrawalCooldown) {
        this.users=users;this.wallets=wallets;this.security=security;this.challenges=challenges;this.currentUser=currentUser;
        this.encoder=encoder;this.delivery=delivery;this.pins=pins;this.sessions=sessions;this.withdrawalCooldown=withdrawalCooldown;
        this.notifications=notifications;
        this.events=events;
        database=new TransactionTemplate(manager);database.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        if(withdrawalCooldown.isNegative())throw new IllegalArgumentException("Invalid withdrawal cooldown");
    }
    public Start requestReset(){return start(WalletPinRecovery.Purpose.RESET_PIN,null);}
    public Start requestRecoveryEmail(@NotNull @Valid Enroll request){
        var auth=pins.authorize(request.pin(),"ENROLL_RECOVERY_EMAIL");
        return start(WalletPinRecovery.Purpose.RECOVERY_EMAIL,auth);
    }
    private Start start(WalletPinRecovery.Purpose purpose,WalletPinService.Authorization auth){
        try {
            return startAttempt(purpose,auth);
        } catch(AppException exception) {
            events.rejection(currentUser.getCurrentUserId(),null,purpose==WalletPinRecovery.Purpose.RESET_PIN?SecurityAction.PIN_RESET_REQUEST:SecurityAction.RECOVERY_EMAIL_REQUEST,exception.getErrorCode().name(),null);
            throw exception;
        }
    }
    private Start startAttempt(WalletPinRecovery.Purpose purpose,WalletPinService.Authorization auth){
        var sender=delivery.getIfAvailable();if(sender==null)throw new AppException(ErrorCode.WALLET_RECOVERY_UNAVAILABLE);
        String code=String.format(java.util.Locale.ROOT,"%08d",random.nextInt(100000000));
        Pending pending=database.execute(tx->{
            UserAccount user=lockUser();var wallet=wallets.findByUserIdForUpdate(user.getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_NOT_FOUND));
            var row=security.findByWalletIdForUpdate(wallet.getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_PIN_NOT_CONFIGURED));
            if(purpose==WalletPinRecovery.Purpose.RECOVERY_EMAIL)pins.requireAuthorization(auth,wallet,"ENROLL_RECOVERY_EMAIL");
            String email=purpose==WalletPinRecovery.Purpose.RESET_PIN?row.getRecoveryEmail():user.getEmail();
            if(email==null||row.getPinHash()==null)throw new AppException(ErrorCode.WALLET_RECOVERY_CONTACT_REQUIRED);
            LocalDateTime now=LocalDateTime.now();
            if(challenges.countByWalletIdAndCreatedAtAfter(wallet.getId(),now.minusHours(1))>=3
                ||challenges.findFirstByWalletIdOrderByCreatedAtDesc(wallet.getId()).filter(c->c.getCreatedAt().isAfter(now.minusMinutes(1))).isPresent())throw new AppException(ErrorCode.WALLET_RECOVERY_RATE_LIMIT);
            challenges.invalidate(wallet.getId(),now);security.flush();
            var challenge=challenges.saveAndFlush(WalletPinRecovery.builder().id(UUID.randomUUID().toString()).wallet(wallet).purpose(purpose)
                .email(email).codeHash(encoder.encode(code)).securityVersion(row.getVersion()).tokenVersion(user.getTokenVersion()).createdAt(now).expiresAt(now.plusMinutes(10)).build());
            events.record(user.getId(),wallet.getId(),purpose==WalletPinRecovery.Purpose.RESET_PIN?SecurityAction.PIN_RESET_REQUEST:SecurityAction.RECOVERY_EMAIL_REQUEST,SecurityOutcome.SUCCESS,"CHALLENGE_CREATED",challenge.getId());
            return new Pending(challenge.getId(),wallet.getId(),email,challenge.getExpiresAt());
        });
        try{sender.send(pending.email(),code,pending.expires());}
        catch(RuntimeException ex){database.executeWithoutResult(tx->{
            challenges.lock(pending.id(),pending.walletId()).ifPresent(c->c.setConsumedAt(LocalDateTime.now()));
            events.record(currentUser.getCurrentUserId(),pending.walletId(),SecurityAction.RECOVERY_DELIVERY,SecurityOutcome.REJECTED,"DELIVERY_UNAVAILABLE",pending.id());
        });throw new AppException(ErrorCode.WALLET_RECOVERY_UNAVAILABLE);}
        database.executeWithoutResult(tx->{
            challenges.lock(pending.id(),pending.walletId()).ifPresent(c->c.setDeliveredAt(LocalDateTime.now()));
            events.record(currentUser.getCurrentUserId(),pending.walletId(),SecurityAction.RECOVERY_DELIVERY,SecurityOutcome.SUCCESS,"DELIVERED",pending.id());
        });
        return new Start(pending.id(),mask(pending.email()),pending.expires());
    }
    public void resetPin(@NotNull @Valid Confirm request){confirm(request.challengeId(),request.code(),request.newPin(),WalletPinRecovery.Purpose.RESET_PIN);}
    public void confirmRecoveryEmail(@NotNull @Valid EmailConfirm request){confirm(request.challengeId(),request.code(),null,WalletPinRecovery.Purpose.RECOVERY_EMAIL);}
    private void confirm(String id,String code,String newPin,WalletPinRecovery.Purpose purpose){
        SecurityAction action=purpose==WalletPinRecovery.Purpose.RESET_PIN?SecurityAction.PIN_RESET:SecurityAction.RECOVERY_EMAIL_CHANGE;
        ErrorCode error;
        try {
            error=database.execute(tx->{
            UserAccount user=lockUser();var wallet=wallets.findByUserIdForUpdate(user.getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_NOT_FOUND));
            var row=security.findByWalletIdForUpdate(wallet.getId()).orElseThrow(()->new AppException(ErrorCode.WALLET_PIN_NOT_CONFIGURED));
            var c=challenges.lock(id,wallet.getId()).orElse(null);LocalDateTime now=LocalDateTime.now();
            if(c==null||c.getPurpose()!=purpose||c.getConsumedAt()!=null||c.getDeliveredAt()==null||!c.getExpiresAt().isAfter(now)
                ||c.getTokenVersion()!=user.getTokenVersion()||!c.getSecurityVersion().equals(row.getVersion())){
                events.record(user.getId(),wallet.getId(),action,SecurityOutcome.REJECTED,"INVALID_CHALLENGE",id);
                return ErrorCode.WALLET_RECOVERY_INVALID;
            }
            if(!encoder.matches(code,c.getCodeHash())){
                c.setFailedAttempts(c.getFailedAttempts()+1);if(c.getFailedAttempts()>=5)c.setConsumedAt(now);
                events.record(user.getId(),wallet.getId(),action,SecurityOutcome.REJECTED,c.getFailedAttempts()>=5?"ATTEMPT_LIMIT":"INCORRECT_CODE",id);
                return ErrorCode.WALLET_RECOVERY_INVALID;
            }
            c.setConsumedAt(now);
            if(purpose==WalletPinRecovery.Purpose.RECOVERY_EMAIL)row.setRecoveryEmail(c.getEmail());
            else{
                row.setPinHash(encoder.encode(newPin));row.setPinChangedAt(now);row.setPinLockedUntil(null);row.setFailedPinAttempts(0);
                row.setWithdrawalBlockedUntil(now.plus(withdrawalCooldown));sessions.revokeAllSessions(user.getId());
            }
            notifications.publish(wallet,"security:"+id,"Thay đổi bảo mật ví",purpose==WalletPinRecovery.Purpose.RESET_PIN?"PIN đã được khôi phục. Các phiên cũ đã bị thu hồi và rút tiền được tạm chặn.":"Email khôi phục ví đã được xác minh.",null);
            events.record(user.getId(),wallet.getId(),action,SecurityOutcome.SUCCESS,"OK",id);
            return null;
            });
        } catch(AppException exception) {
            events.rejection(currentUser.getCurrentUserId(),null,action,exception.getErrorCode().name(),id);
            throw exception;
        }
        if(error!=null)throw new AppException(error);
    }
    private UserAccount lockUser(){
        var user=users.findByIdForUpdate(currentUser.getCurrentUserId()).orElseThrow(()->new AppException(ErrorCode.USER_NOT_FOUND));
        if(!user.isActive())throw new AppException(ErrorCode.USER_LOCKED);
        if(user.getTokenVersion()!=currentUser.getCurrentPrincipal().getTokenVersion())throw new AppException(ErrorCode.TOKEN_INVALID);
        return user;
    }
    private String mask(String email){int at=email.indexOf('@');return at<1?"***":email.substring(0,1)+"***"+email.substring(at);}
    private record Pending(String id,Long walletId,String email,LocalDateTime expires){}
}
