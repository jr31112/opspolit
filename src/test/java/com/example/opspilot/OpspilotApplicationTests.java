package com.example.opspilot;

import com.example.opspilot.repository.HelloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpspilotApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired HelloRepository repository;

    @BeforeEach void clean() { repository.deleteAll(); }

    @Test void hello() throws Exception {
        mvc.perform(get("/hello/")).andExpect(status().isOk()).andExpect(content().string("hello"));
    }

    @Test void userLifecycle() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(content().json("[]"));
        var result = mvc.perform(post("/api/users").contentType("application/json").content("{\"name\":\"Alice\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Alice")).andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).startsWith("/api/users/");
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Alice"));
        mvc.perform(get("/api/users")).andExpect(jsonPath("$[0].name").value("Alice"));
        mvc.perform(put(location).contentType("application/json").content("{\"name\":\"Bob\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bob"));
        mvc.perform(get(location)).andExpect(jsonPath("$.name").value("Bob"));
        assertThat(repository.findAll()).singleElement().extracting("name").isEqualTo("Bob");
        mvc.perform(delete(location)).andExpect(status().isNoContent());
        mvc.perform(get(location)).andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }

    @Test void missingUsers() throws Exception {
        mvc.perform(get("/api/users/99999")).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/99999").contentType("application/json").content("{\"name\":\"Bob\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/99999")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"  \"}", "not-json"})
    void invalidUsers(String body) throws Exception {
        mvc.perform(post("/api/users").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/users/1").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test void longName() throws Exception {
        mvc.perform(post("/api/users").contentType("application/json")
            .content("{\"name\":\"" + "a".repeat(101) + "\"}" )).andExpect(status().isBadRequest());
    }

    @Test void latency() throws Exception {
        long start = System.nanoTime();
        mvc.perform(post("/api/test/latency").param("durationMs", "30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.durationMs").value(30));
        assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(30_000_000L);
    }
    @Test void cpu() throws Exception {
        long start = System.nanoTime();
        mvc.perform(post("/api/test/cpu").param("durationMs", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("cpu"));
        assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(20_000_000L);
    }
    @Test void memory() throws Exception {
        mvc.perform(post("/api/test/memory").param("sizeMb", "1").param("durationMs", "10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sizeMb").value(1));
    }
    @Test void intentionalError() throws Exception {
        mvc.perform(post("/api/test/error")).andExpect(status().isInternalServerError());
    }
    @ParameterizedTest
    @ValueSource(strings = {"latency", "cpu", "memory"})
    void invalidDuration(String endpoint) throws Exception {
        for (String value : new String[]{"0", "-1", "30001", "abc"})
            mvc.perform(post("/api/test/" + endpoint).param("durationMs", value)).andExpect(status().isBadRequest());
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "129", "2147483647", "abc"})
    void invalidMemory(String value) throws Exception {
        mvc.perform(post("/api/test/memory").param("sizeMb", value)).andExpect(status().isBadRequest());
    }
    @Test void actuator() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isOk()).andExpect(jsonPath("$.names").isArray());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
            .andExpect(content().string(containsString("jvm_memory_used_bytes")))
            .andExpect(content().string(containsString("http_server_requests_seconds")));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
