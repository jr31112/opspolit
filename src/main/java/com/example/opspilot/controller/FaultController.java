package com.example.opspilot.controller;

import java.util.Map;
import com.example.opspilot.service.FaultService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "opspilot.faults.enabled", havingValue = "true")
public class FaultController {
    private final FaultService service;

    @PostMapping("/latency")
    public Map<String, Object> latency(@RequestParam(defaultValue = "1000") @Min(1) @Max(30000) long durationMs) {
        service.latency(durationMs);
        return Map.of("type", "latency", "durationMs", durationMs);
    }
    @PostMapping("/cpu")
    public Map<String, Object> cpu(@RequestParam(defaultValue = "1000") @Min(1) @Max(30000) long durationMs) {
        service.cpu(durationMs);
        return Map.of("type", "cpu", "durationMs", durationMs);
    }
    @PostMapping("/memory")
    public Map<String, Object> memory(
            @RequestParam(defaultValue = "32") @Min(1) @Max(128) int sizeMb,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(30000) long durationMs) {
        service.memory(sizeMb, durationMs);
        return Map.of("type", "memory", "sizeMb", sizeMb, "durationMs", durationMs);
    }
    @PostMapping("/error")
    public void error() {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Intentional test error");
    }
}
