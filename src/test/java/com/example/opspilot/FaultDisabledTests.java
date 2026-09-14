package com.example.opspilot;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "opspilot.faults.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FaultDisabledTests {
    @Autowired MockMvc mvc;
    @ParameterizedTest
    @ValueSource(strings = {"latency", "cpu", "memory", "error"})
    void disabled(String endpoint) throws Exception {
        mvc.perform(post("/api/test/" + endpoint)).andExpect(status().isNotFound());
    }
}
