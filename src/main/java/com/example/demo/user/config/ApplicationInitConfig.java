package com.example.demo.user.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class ApplicationInitConfig {

    private final PasswordEncoder passwordEncoder;

    /**
     * Tạo tài khoản admin đầu tiên nếu chưa có.
     *
     * <p>Mật khẩu lấy từ biến môi trường {@code ADMIN_INITIAL_PASSWORD}. Nếu không
     * cấu hình, sinh mật khẩu ngẫu nhiên và in ra log một lần — không dùng mật khẩu
     * mặc định cố định như {@code admin/admin} vì rất dễ bị quét và chiếm quyền.
     */
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository) {
        return args -> {
            if (userRepository.existsByUsername("admin")) {
                return;
            }

            String initialPassword = System.getenv("ADMIN_INITIAL_PASSWORD");
            boolean generated = initialPassword == null || initialPassword.isBlank();
            if (generated) {
                initialPassword = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }

            User adminUser = User.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode(initialPassword))
                    .role(UserRole.ADMIN)
                    .build();

            userRepository.save(adminUser);

            if (generated) {
                log.warn("Đã tạo tài khoản admin với mật khẩu tạm thời: {}. "
                        + "HÃY ĐỔI MẬT KHẨU NGAY sau lần đăng nhập đầu tiên.", initialPassword);
            } else {
                log.warn("Đã tạo tài khoản admin với mật khẩu từ ADMIN_INITIAL_PASSWORD. Hãy đổi mật khẩu sau khi đăng nhập.");
            }
        };
    }
}
