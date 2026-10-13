package com.example.demo.user.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.demo.user.entity.User;
import com.example.demo.user.repository.InvalidatedTokenRepository;
import com.example.demo.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Xác thực request bằng JWT.
 *
 * <p>Thứ tự kiểm tra:
 * <ol>
 *   <li>chữ ký + hạn của token;</li>
 *   <li>token có nằm trong blacklist (đã logout / đổi mật khẩu) hay không;</li>
 *   <li>người dùng còn tồn tại và chưa bị khóa hay không.</li>
 * </ol>
 *
 * <p>Role được đọc từ DB thay vì từ claim, để việc thay đổi quyền có hiệu lực ngay.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final InvalidatedTokenRepository invalidatedTokenRepository;

    public JwtAuthenticationFilter(JwtUtils jwtUtils,
                                   UserRepository userRepository,
                                   InvalidatedTokenRepository invalidatedTokenRepository) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.invalidatedTokenRepository = invalidatedTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String jwt = jwtUtils.getJwtFromRequest(request);

        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(jwt, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        if (!jwtUtils.validateJwtToken(jwt)) {
            return;
        }

        String jwtId;
        Long userId;
        try {
            jwtId = jwtUtils.getJwtIdFromJwtToken(jwt);
            userId = Long.valueOf(jwtUtils.getUserIdFromJwtToken(jwt));
        } catch (Exception e) {
            log.debug("Không đọc được claim của token: {}", e.getMessage());
            return;
        }

        // Token đã bị thu hồi (logout, đổi mật khẩu) -> từ chối.
        if (jwtId != null && invalidatedTokenRepository.existsById(jwtId)) {
            log.debug("Token đã bị thu hồi, bỏ qua xác thực (jti={})", jwtId);
            return;
        }

        // Tài khoản phải còn tồn tại và đang hoạt động.
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            log.debug("Token trỏ tới user không tồn tại (id={})", userId);
            return;
        }

        UserPrincipal principal = new UserPrincipal(userOptional.get());
        if (!principal.isEnabled()) {
            log.debug("Tài khoản đã bị khóa, bỏ qua xác thực (id={})", userId);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
