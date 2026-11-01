package com.example.demo.shared.logging;

import java.util.Map;
import org.slf4j.MDC;

/** Restores the previous context, including on pooled worker threads. */
public final class LogContext implements AutoCloseable {
    private final Map<String, String> previous = MDC.getCopyOfContextMap();
    public LogContext(Map<String, String> fields) { fields.forEach(MDC::put); }
    @Override public void close() {
        MDC.clear();
        if (previous != null) MDC.setContextMap(previous);
    }
}
