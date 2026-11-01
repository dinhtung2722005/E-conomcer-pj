package com.example.demo.observability;
import com.example.demo.shared.logging.LogSanitizer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import tools.jackson.databind.ObjectMapper;

/** One JSON object per line; never serializes argument objects or throwable messages. */
public class SafeJsonLogEncoder extends EncoderBase<ILoggingEvent> {
    private final ObjectMapper json = new ObjectMapper();
    private static final Set<String> CONTEXT = Set.of("requestId", "userId", "walletId", "orderId",
            "transactionId", "jobRunId", "transferId", "eventKey", "journalId");

    @Override public byte[] headerBytes() { return null; }
    @Override public byte[] footerBytes() { return null; }

    @Override public byte[] encode(ILoggingEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        fields.put("level", event.getLevel().toString());
        fields.put("logger", event.getLoggerName());
        fields.put("message", LogSanitizer.redact(event.getFormattedMessage()));
        event.getMDCPropertyMap().forEach((key, value) -> {
            if (CONTEXT.contains(key)) fields.put(key, LogSanitizer.redact(value));
        });
        // Only explicitly named, safe scalar fields are accepted from fluent SLF4J logging.
        if (event.getKeyValuePairs() != null) event.getKeyValuePairs().forEach(pair -> {
            if (Set.of("event", "method", "route", "status", "durationMs", "currency", "walletIds")
                    .contains(pair.key) && (pair.value instanceof String || pair.value instanceof Number)) {
                fields.put(pair.key, pair.value instanceof String s ? LogSanitizer.redact(s) : pair.value);
            }
        });
        if (event.getThrowableProxy() != null) {
            fields.put("exceptionType", event.getThrowableProxy().getClassName());
            var frames = event.getThrowableProxy().getStackTraceElementProxyArray();
            if (frames != null) fields.put("stack", Arrays.stream(frames).limit(24)
                    .map(frame -> frame.getStackTraceElement().toString()).toList());
        }
        return (json.writeValueAsString(fields) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
