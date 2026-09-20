package com.example.opspilot.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.event.Level;

/** Shared field names and order for request completion and exception logs. */
final class HttpRequestLog {
    static final String START_NANOS = HttpRequestLog.class.getName() + ".startNanos";
    static final String STARTED_AT = HttpRequestLog.class.getName() + ".startedAt";
    static final String FORMAT = "startedAt={} level={} requestId={} method={} path={} status={} latencyMs={} exception={}";

    private HttpRequestLog() {}

    static void write(Logger logger, Level level, HttpServletRequest request,
                      int status, Exception exception) {
        Long start = (Long) request.getAttribute(START_NANOS);
        Object latencyMs = start == null ? "-" : (System.nanoTime() - start) / 1_000_000.0;
        Object startedAt = request.getAttribute(STARTED_AT);
        var entry = logger.atLevel(level);
        if (level == Level.ERROR && exception != null) {
            entry.setCause(exception);
        }
        entry.log(FORMAT, startedAt == null ? "-" : startedAt, level,
                request.getAttribute(RequestLoggingInterceptor.REQUEST_ID), request.getMethod(),
                request.getRequestURI().replaceAll("[\\p{Cntrl}\\s]", "_"), status, latencyMs,
                exception == null ? "-" : exception.getClass().getSimpleName());
    }
}
