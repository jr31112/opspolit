package com.example.opspilot.service;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FaultService {

    private static final int BYTES_PER_MIB = 1024 * 1024;
    private static final int CPU_ITERATIONS_PER_BATCH = 1024;

    // CPU와 메모리 테스트는 인스턴스당 합계 한 요청만 실행한다.
    private final Semaphore resourceTestPermit = new Semaphore(1);

    // 연산 결과를 보관해 CPU 부하 루프가 불필요한 연산으로 제거되지 않게 한다.
    private volatile long checksum;

    public void latency(long durationMs) {
        pause(durationMs);
    }

    public void cpu(long durationMs) {
        acquireResourceTestPermit();

        try {
            long startedAtNanos = System.nanoTime();
            long durationNanos = TimeUnit.MILLISECONDS.toNanos(durationMs);
            long value = 1;

            while (System.nanoTime() - startedAtNanos < durationNanos) {
                for (int iteration = 0; iteration < CPU_ITERATIONS_PER_BATCH; iteration++) {
                    value = value * 1664525 + 1013904223;
                }

                if (Thread.currentThread().isInterrupted()) {
                    throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
                }
            }

            checksum = value;
        } finally {
            resourceTestPermit.release();
        }
    }

    public void memory(int sizeMb, long durationMs) {
        acquireResourceTestPermit();

        try {
            byte[] allocatedMemory = new byte[sizeMb * BYTES_PER_MIB];
            Arrays.fill(allocatedMemory, (byte) 1);

            try {
                pause(durationMs);
            } finally {
                // 대기가 끝날 때까지 할당한 메모리가 GC 대상이 되지 않도록 유지한다.
                Reference.reachabilityFence(allocatedMemory);
            }
        } finally {
            resourceTestPermit.release();
        }
    }

    private void acquireResourceTestPermit() {
        if (!resourceTestPermit.tryAcquire()) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "CPU/memory test already running");
        }
    }

    private void pause(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
        }
    }
}
