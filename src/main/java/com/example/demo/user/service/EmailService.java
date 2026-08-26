// package com.example.demo.user.service;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.mail.SimpleMailMessage;
// import org.springframework.mail.javamail.JavaMailSender;
// import org.springframework.stereotype.Service;

// @Service
// public class EmailService {

//     @Autowired
//     private JavaMailSender mailSender;

//     public void sendOtpEmail(String toEmail, String otpCode) {
//         SimpleMailMessage message = new SimpleMailMessage();
        
//         message.setTo(toEmail);
//         message.setSubject("Mã OTP Khôi phục mật khẩu");
//         message.setText("Xin chào,\n\n" +
//                         "Bạn đã yêu cầu khôi phục mật khẩu. Mã xác thực OTP của bạn là: " + otpCode + "\n\n" +
//                         "Mã này sẽ hết hạn sau 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\n" +
//                         "Trân trọng,\nĐội ngũ Mini Shopee");
                        
//         mailSender.send(message);
//     }
// }