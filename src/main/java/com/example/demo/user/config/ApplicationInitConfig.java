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
     * cấu hình, bỏ qua khởi tạo admin. Không sinh hoặc in mật khẩu ra log.
     */
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Value("${ADMIN_INITIAL_PASSWORD:}") String initialPassword) {
        return args -> {
            if (userRepository.existsByUsername("admin")) {
                return;
            }

            if (initialPassword == null || initialPassword.isBlank()) {
                log.warn("Bỏ qua khởi tạo admin: cần cấu hình ADMIN_INITIAL_PASSWORD qua môi trường triển khai.");
                return;
            }

            User adminUser = User.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode(initialPassword))
                    .role(UserRole.ADMIN)
                    .build();

            userRepository.save(adminUser);

            log.warn("Đã khởi tạo tài khoản admin. Hãy đổi mật khẩu sau khi đăng nhập.");
        };
    }
}
