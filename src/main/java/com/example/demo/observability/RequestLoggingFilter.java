package com.example.demo.observability;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import com.example.demo.wallet.entity.WalletSecurityEvent.Action;
import com.example.demo.wallet.service.WalletSecurityEventService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component @Order(Ordered.HIGHEST_PRECEDENCE + 10) @RequiredArgsConstructor @Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    private final WalletSecurityEventService securityEvents;

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-Id");
        String requestId = supplied != null && supplied.matches("[A-Za-z0-9_-]{8,80}")
                ? supplied : UUID.randomUUID().toString();
        long start = System.nanoTime();
        boolean completed = false;
        try (var context = new LogContext(Map.of("requestId", requestId))) {
            response.setHeader("X-Request-Id", requestId);
            try {
                chain.doFilter(request, response);
                completed = true;
            } finally {
                int status = completed ? response.getStatus() : 500;
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                // The framework's route template, never a raw URL, query string, header or body.
                log.atInfo().addKeyValue("event", "HTTP_REQUEST")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("route", route == null ? "UNMATCHED" : route.toString())
                        .addKeyValue("status", status)
                        .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
                        .log("HTTP request completed");
                if (request.getRequestURI().startsWith("/api/") && (status == 401 || status == 403)) {
                    try {
                        securityEvents.rejection((Long) request.getAttribute("audit.actorId"), null,
                                status == 403 ? Action.ACCESS_DENIED : Action.AUTHENTICATION_FAILED,
                                "HTTP_" + status, null);
                    } catch (RuntimeException exception) {
                        // Never replace a denied response with an audit/database error.
                        log.error("Security rejection audit unavailable ({})", exception.getClass().getSimpleName());
                    }
                }
            }
        }
    }
}
