package com.example.opspilot.web;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object requestId = request.getAttribute(RequestLoggingInterceptor.REQUEST_ID, WebRequest.SCOPE_REQUEST);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            request.setAttribute(RequestLoggingInterceptor.REQUEST_ID, requestId, WebRequest.SCOPE_REQUEST);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.set(RequestLoggingInterceptor.HEADER, requestId.toString());
        HttpRequestLog.write(log, status.is5xxServerError() ? Level.ERROR : Level.WARN,
                ((ServletWebRequest) request).getRequest(), status.value(), ex);
        HttpStatus known = HttpStatus.resolve(status.value());
        String detail = status.is5xxServerError() ? "An internal server error occurred."
                : known != null ? known.getReasonPhrase() : "Request failed.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("requestId", requestId);
        return super.handleExceptionInternal(ex, problem, responseHeaders, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
