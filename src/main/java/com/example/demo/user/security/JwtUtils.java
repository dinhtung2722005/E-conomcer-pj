package com.example.demo.user.security;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.demo.user.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Sinh và kiểm tra JWT.
 *
 * <p>Access token mang claim {@code scope} = role và {@code jti} = id duy nhất
 * (dùng cho blacklist khi logout). Thời gian sống lấy từ cấu hình
 * {@code app.jwt.expiration-ms}.
 */
@Component
@Slf4j
public class JwtUtils {

    public static final String ACCESS_COOKIE = "jwt";
    public static final String REFRESH_COOKIE = "refresh_jwt";

    /** Thuật toán ký cố định, không cho phép client tự chọn alg (chống alg-confusion). */
    private static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS256;

    private final Key signingKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtils(
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.signingKey = buildSigningKey(jwtSecret);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Khóa ký lấy từ biến môi trường {@code JWT_SECRET} (chuỗi Base64 ≥ 256 bit).
     * Nếu chưa cấu hình, sinh khóa ngẫu nhiên cho môi trường dev — token sẽ hết
     * hiệu lực sau mỗi lần khởi động lại.
     */
    private static Key buildSigningKey(String jwtSecret) {
        if (!StringUtils.hasText(jwtSecret)) {
            log.warn("Chưa cấu hình app.jwt.secret (biến môi trường JWT_SECRET). "
                    + "Đang dùng khóa ngẫu nhiên tạm thời — mọi token sẽ mất hiệu lực khi restart.");
            return Keys.secretKeyFor(SIGNATURE_ALGORITHM);
        }
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret.trim()));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "app.jwt.secret không hợp lệ: cần chuỗi Base64 dài tối thiểu 256 bit (32 byte).", e);
        }
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private Key getSigningKey() {
        return signingKey;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .setId(UUID.randomUUID().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("scope", user.getRole().name())
                .claim("tokenVersion", user.getTokenVersion())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(getSigningKey(), SIGNATURE_ALGORITHM)
                .compact();
    }

    /**
     * Refresh token là chuỗi ngẫu nhiên (không phải JWT) và được đối chiếu với bảng
     * {@code refresh_tokens}, nhờ vậy có thể thu hồi/rotate.
     */
    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString() + UUID.randomUUID();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getUserIdFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUsernameFromJwtToken(String token) {
        return parseClaims(token).get("username", String.class);
    }

    public String getRoleFromJwtToken(String token) {
        return parseClaims(token).get("scope", String.class);
    }

    /** Token trước khi có tokenVersion được xem là phiên bản 0 để chuyển đổi dữ liệu cũ. */
    public long getTokenVersion(String token) {
        Number version = parseClaims(token).get("tokenVersion", Number.class);
        return version == null ? 0L : version.longValue();
    }

    public String getJwtIdFromJwtToken(String token) {
        return parseClaims(token).getId();
    }

    public Date getExpirationDateFromJwtToken(String token) {
        return parseClaims(token).getExpiration();
    }

    public boolean validateJwtToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token không hợp lệ ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    /** Ưu tiên header {@code Authorization: Bearer ...}, sau đó tới cookie. */
    public String getJwtFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return getCookieValue(request, ACCESS_COOKIE);
    }

    public String getRefreshTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Refresh-Token");
        if (StringUtils.hasText(header)) {
            return header;
        }
        return getCookieValue(request, REFRESH_COOKIE);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
