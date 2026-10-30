package com.example.demo.wallet.gateway;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="wallet.recovery.email-enabled",havingValue="true")
public class SmtpWalletRecoveryDelivery implements WalletRecoveryDelivery {
    private final JavaMailSender mail;
    private final String from;
    public SmtpWalletRecoveryDelivery(JavaMailSender mail,@Value("${wallet.recovery.email-from}") String from){this.mail=mail;this.from=from;}
    public void send(String recipient,String code,LocalDateTime expiresAt){
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(recipient);
        message.setSubject("Xác minh bảo mật ví");
        message.setText("Mã xác minh ví: "+code+"\nHết hạn lúc: "+expiresAt+"\nKhông chia sẻ mã này. Nếu không yêu cầu, hãy khóa ví và liên hệ hỗ trợ.");
        mail.send(message);
    }
}
