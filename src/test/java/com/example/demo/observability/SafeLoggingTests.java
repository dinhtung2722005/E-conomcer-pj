package com.example.demo.observability;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.rolling.*;
import ch.qos.logback.core.util.FileSize;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import com.example.demo.wallet.entity.WalletSecurityEvent.Action;
import com.example.demo.wallet.service.WalletSecurityEventService;
import tools.jackson.databind.ObjectMapper;

class SafeLoggingTests {
    @AfterEach void clear() { MDC.clear(); }

    @Test void encoderRedactsCredentialsAndOmitsThrowableMessagesAndUnknownContext() {
        var context = new LoggerContext();
        var event = new LoggingEvent("test", context.getLogger("test"), Level.ERROR,
                "password={} pin=123456 otp=87654321 {\"accessToken\":\"private-token\"} "
                + "Authorization: Bearer abc.def.ghi Duplicate entry 'private-db-value' for key username",
                new IllegalStateException("private-exception-value"), new Object[]{"private-password"});
        event.setMDCPropertyMap(Map.of("requestId", "request-safe-123", "cookie", "private-cookie"));
        var encoder = new SafeJsonLogEncoder();
        String encoded = new String(encoder.encode(event), StandardCharsets.UTF_8);
        for (String secret : List.of("private-password", "123456", "87654321", "private-token", "abc.def.ghi",
                "private-db-value", "private-exception-value", "private-cookie")) assertFalse(encoded.contains(secret), secret);
        var json = new ObjectMapper().readTree(encoded);
        assertEquals("request-safe-123", json.get("requestId").asText());
        assertEquals(IllegalStateException.class.getName(), json.get("exceptionType").asText());
        assertEquals(1, encoded.lines().count());
        context.stop();
    }

    @Test void filterCorrelatesRequestsRejectsUnsafeIdsAndDoesNotLeakContext() throws Exception {
        var events = mock(WalletSecurityEventService.class);
        var filter = new RequestLoggingFilter(events);
        var request = new MockHttpServletRequest("POST", "/api/wallet/pin");
        request.addHeader("X-Request-Id", "unsafe\r\nsecret");
        request.setQueryString("pin=123456");
        request.setContent("{\"pin\":\"123456\"}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertEquals(response.getHeader("X-Request-Id"), MDC.get("requestId"));
            assertDoesNotThrow(() -> UUID.fromString(MDC.get("requestId")));
            MDC.put("userId", "7");
        });
        assertNull(MDC.get("requestId"));
        assertNull(MDC.get("userId"));
        verifyNoInteractions(events);
        var second = new MockHttpServletRequest("GET", "/api/admin/wallet-ops/audit");
        second.addHeader("X-Request-Id", "valid-request-123");
        second.setAttribute("audit.actorId", 7L);
        var denied = new MockHttpServletResponse();
        filter.doFilter(second, denied, (req,res) -> denied.setStatus(403));
        assertEquals("valid-request-123", denied.getHeader("X-Request-Id"));
        verify(events).rejection(7L, null, Action.ACCESS_DENIED, "HTTP_403", null);
        assertNull(MDC.get("requestId"));
    }

    @Test void auditFailureDoesNotTurnDeniedRequestIntoSuccess() throws Exception {
        var events = mock(WalletSecurityEventService.class);
        doThrow(new IllegalStateException("private database failure")).when(events)
                .rejection(null, null, Action.AUTHENTICATION_FAILED, "HTTP_401", null);
        var response = new MockHttpServletResponse();
        new RequestLoggingFilter(events).doFilter(new MockHttpServletRequest("GET", "/api/wallet"), response,
                (req,res) -> response.setStatus(401));
        assertEquals(401, response.getStatus());
        assertNull(MDC.get("requestId"));
    }

    @Test void rollingFileWritesSanitizedJsonAndCompressesArchives(@TempDir Path directory) throws Exception {
        var context = new LoggerContext();
        context.start();
        var appender = new RollingFileAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.setContext(context);
        appender.setName("test-file");
        appender.setFile(directory.resolve("application.jsonl").toString());
        var encoder = new SafeJsonLogEncoder(); encoder.setContext(context); encoder.start(); appender.setEncoder(encoder);
        var policy = new SizeAndTimeBasedRollingPolicy<ch.qos.logback.classic.spi.ILoggingEvent>();
        policy.setContext(context); policy.setParent(appender);
        policy.setFileNamePattern(directory.resolve("archive.%d{yyyy-MM-dd}.%i.jsonl.gz").toString());
        policy.setMaxFileSize(FileSize.valueOf("1KB")); policy.setMaxHistory(2);
        policy.setTotalSizeCap(FileSize.valueOf("1MB")); policy.start();
        appender.setRollingPolicy(policy); appender.start();
        assertTrue(appender.isStarted());
        for (int i = 0; i < 100; i++) {
            var event = new LoggingEvent("test", context.getLogger("rolling"), Level.INFO,
                    "pin=123456 " + "safe ".repeat(100), null, null);
            event.setMDCPropertyMap(Map.of()); appender.doAppend(event);
        }
        appender.stop(); policy.stop(); context.stop();
        assertTrue(Files.exists(directory.resolve("application.jsonl")));
        try (var paths = Files.list(directory)) {
            var archives = paths.filter(p -> p.toString().endsWith(".gz")).toList();
            assertFalse(archives.isEmpty());
            for (var archive : archives) try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(archive))) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(content.contains("123456"));
                content.lines().forEach(line -> assertDoesNotThrow(() -> new ObjectMapper().readTree(line)));
            }
        }
    }
}
