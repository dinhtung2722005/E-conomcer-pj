package com.example.demo.observability;

import java.util.regex.Pattern;

/** Defence in depth. Callers must still avoid logging request bodies and credentials. */
public final class LogSanitizer {
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)([\\\"']?(?:[a-z_]*(?:password|passwd|pin|otp|secret|token|authorization|cookie)|code|accountToken)[\\\"']?\\s*[:=]\\s*)"
            + "(\\\"(?:\\\\.|[^\\\"])*\\\"|'(?:\\\\.|[^'])*'|[^\\s,;}]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[a-z0-9._~+/=-]+");
    private static final Pattern HEADER = Pattern.compile("(?i)(\\b(?:authorization|proxy-authorization|cookie|set-cookie)\\s*[:=]\\s*)[^\\r\\n]*");
    private static final Pattern JWT = Pattern.compile("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
    private static final Pattern DUPLICATE = Pattern.compile("(?i)(Duplicate entry )'[^']*'");

    private LogSanitizer() {}

    public static String redact(String value) {
        if (value == null) return null;
        String safe = BEARER.matcher(value).replaceAll("Bearer [REDACTED]");
        safe = HEADER.matcher(safe).replaceAll("$1[REDACTED]");
        safe = CREDENTIAL.matcher(safe).replaceAll("$1[REDACTED]");
        safe = JWT.matcher(safe).replaceAll("[REDACTED_JWT]");
        return DUPLICATE.matcher(safe).replaceAll("$1'[REDACTED]'");
    }
}
