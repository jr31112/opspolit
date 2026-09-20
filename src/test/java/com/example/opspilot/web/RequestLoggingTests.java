package com.example.opspilot.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RequestLoggingTests {
    private MockMvc mvc;
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingInterceptor.class);
    private final Logger errorLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> errors = new ListAppender<>();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach void setup() {
        errors.start();
        errorLogger.addAppender(errors);
        appender.start();
        logger.addAppender(appender);
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addInterceptors(new RequestLoggingInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void cleanup() {
        logger.detachAppender(appender);
        appender.stop();
        errorLogger.detachAppender(errors);
        errors.stop();
        MDC.clear();
    }

    @Test void logsLatencyAndPropagatesIdWithoutQueryOrBody() throws Exception {
        mvc.perform(get("/probe").queryParam("token", "secret-value").header("X-Request-ID", "request-123"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "request-123"));
        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).contains("requestId=request-123", "startedAt=", "method=GET",
                "path=/probe", "status=200", "latencyMs=").doesNotContain("secret-value", "token");
        assertThat((Double) event.getArgumentArray()[6]).isGreaterThanOrEqualTo(15.0);
        assertThat(event.getFormattedMessage()).matches(
                "startedAt=\\S+ level=INFO requestId=request-123 method=GET path=/probe status=200 latencyMs=\\S+ exception=-");
        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
        assertThat(event.getFormattedMessage()).doesNotContain("event=");
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void generatesDistinctIdsAndReplacesInvalidIds() throws Exception {
        String first = mvc.perform(get("/probe")).andReturn().getResponse().getHeader("X-Request-ID");
        String second = mvc.perform(get("/probe").header("X-Request-ID", "invalid id"))
                .andReturn().getResponse().getHeader("X-Request-ID");
        assertThat(UUID.fromString(first)).isNotEqualTo(UUID.fromString(second));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void hidesUnexpectedErrorAndCorrelatesResponseAndLog() throws Exception {
        mvc.perform(get("/unexpected").header("X-Request-ID", "failure-123"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-ID", "failure-123"))
                .andExpect(jsonPath("$.requestId").value("failure-123"))
                .andExpect(jsonPath("$.detail").value("An internal server error occurred."));
        assertThat(appender.list).hasSize(1);
        assertThat(errors.list).hasSize(1);
        assertThat(errors.list.get(0).getMessage()).isEqualTo(appender.list.get(0).getMessage());
        assertThat(errors.list.get(0).getFormattedMessage()).contains(
                "method=GET", "path=/unexpected", "status=500", "requestId=failure-123",
                "exception=IllegalStateException");
        assertThat(errors.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
        assertThat(errors.list.get(0).getThrowableProxy()).isNotNull();
        assertThat(appender.list.get(0).getFormattedMessage()).contains("status=500", "requestId=failure-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void preservesFrameworkAndExplicitErrorStatuses() throws Exception {
        mvc.perform(get("/limited")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.requestId").isString());
        mvc.perform(get("/number").param("value", "abc")).andExpect(status().isBadRequest());
        mvc.perform(post("/probe")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.requestId").isString());
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void mappingFailureKeepsFormatWithUnknownTiming() throws Exception {
        var response = mvc.perform(post("/probe")).andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse();
        assertThat(errors.list).hasSize(1);
        assertThat(errors.list.get(0).getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(errors.list.get(0).getMessage()).isEqualTo(HttpRequestLog.FORMAT);
        assertThat(errors.list.get(0).getFormattedMessage()).contains("startedAt=-", "latencyMs=-",
                "method=POST", "path=/probe", "requestId=" + response.getHeader("X-Request-ID"));
    }

    @Test void asyncDispatchLogsOnceAndCleansRequestThreadMdc() throws Exception {
        var result = mvc.perform(get("/async").header("X-Request-ID", "async-123"))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(appender.list).isEmpty();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "async-123"));
        assertThat(appender.list).hasSize(1);
        assertThat(MDC.get("requestId")).isNull();
    }

    @RestController
    static class TestController {
        @GetMapping("/async") java.util.concurrent.Callable<String> async() { return () -> "ok"; }
        @GetMapping("/probe") String probe() throws InterruptedException { Thread.sleep(15); return "ok"; }
        @GetMapping("/unexpected") String unexpected() { throw new IllegalStateException("private database details"); }
        @GetMapping("/limited") String limited() { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS); }
        @GetMapping("/number") int number(@RequestParam int value) { return value; }
    }
}
