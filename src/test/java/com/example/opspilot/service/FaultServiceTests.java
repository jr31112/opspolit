package com.example.opspilot.service;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaultServiceTests {
    @Test void rejectsConcurrentWorkAndReleasesPermitAfterInterruption() throws Exception {
        FaultService service = new FaultService();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { service.memory(1, 30000); }
            catch (Throwable e) { failure.set(e); }
        });
        worker.start();
        try {
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (worker.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline)
                Thread.sleep(1);
            assertThat(worker.getState()).isEqualTo(Thread.State.TIMED_WAITING);
            assertThatThrownBy(() -> service.cpu(1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
            assertThatThrownBy(() -> service.memory(1, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
        } finally {
            worker.interrupt();
            worker.join(2000);
        }
        assertThat(worker.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        service.cpu(1);
        service.memory(1, 1);
    }
}
