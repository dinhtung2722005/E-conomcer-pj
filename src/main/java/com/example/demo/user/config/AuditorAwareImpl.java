package com.example.demo.user.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Nếu chưa đăng nhập (ví dụ lúc đăng ký tài khoản mới) -> Ghi nhận là SYSTEM
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of("SYSTEM");
        }

        // Trả về username của người đang thực hiện request
        return Optional.of(authentication.getName());
    }
}