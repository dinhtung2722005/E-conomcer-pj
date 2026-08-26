package com.example.demo.user.sercurity;

import java.io.IOException;
import java.util.Collections;

import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j // Lombok sẽ tạo ra biến 'log'
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, @Lazy UserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = jwtUtils.getJwtFromCookies(request);

        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            
            if (jwtUtils.validateJwtToken(jwt)) {
                try {
                    String username = jwtUtils.getUsernameFromJwtToken(jwt);
                    String role = jwtUtils.getRoleFromJwtToken(jwt); 
                    
                 
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role);
                    
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username, // 
                            null,
                            Collections.singletonList(authority)
                    );
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    
                } catch (Exception e) {
                    log.error("Cannot set user authentication: {}", e.getMessage());
                }
            }
        }
        
        
        filterChain.doFilter(request, response);
    }
}