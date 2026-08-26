package com.example.demo.user.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserRole;
import com.example.demo.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;
@Configuration
@Slf4j
public class ApplicationInitConfig {
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                var role = UserRole.ADMIN;
                User adminUser = User.builder()
                        .username("admin")
                        .email("admin@example.com")
                        .password(passwordEncoder.encode("admin"))
                        .role(role)
                        .build();
                userRepository.save(adminUser);
                log.warn("Admin user created with username: 'admin' and password: 'admin'. Please change the password after first login.");
            }
        };
    }
}
