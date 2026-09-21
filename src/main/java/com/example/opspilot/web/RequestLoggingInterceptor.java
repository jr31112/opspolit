package com.example.opspilot.web;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
public class RequestLoggingInterceptor implements AsyncHandlerInterceptor {
    public static final String REQUEST_ID = "requestId";
    public static final String HEADER = "X-Request-ID";
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(HttpRequestLog.START_NANOS) == null) {
            request.setAttribute(HttpRequestLog.START_NANOS, System.nanoTime());
            request.setAttribute(HttpRequestLog.STARTED_AT, Instant.now());
            String supplied = request.getHeader(HEADER);
            String id = supplied != null && VALID_ID.matcher(supplied).matches()
                    ? supplied : UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID, id);
        }
        String id = (String) request.getAttribute(REQUEST_ID);
        MDC.put(REQUEST_ID, id);
        response.setHeader(HEADER, id);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        try {
            if (request.getAttribute(HttpRequestLog.START_NANOS) != null) {
                HttpRequestLog.write(log, Level.INFO, request, response.getStatus(), exception);
                request.removeAttribute(HttpRequestLog.START_NANOS);
                request.removeAttribute(HttpRequestLog.STARTED_AT);
            }
        } finally {
            MDC.remove(REQUEST_ID);
        }
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                              Object handler) {
        MDC.remove(REQUEST_ID);
    }
}
