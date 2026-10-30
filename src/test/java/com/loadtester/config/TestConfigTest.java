package com.loadtester.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestConfigTest {

    @Test
    void requestBodyIsDefensivelyCopied() {
        byte[] body = {1, 2, 3};
        TestConfig config = TestConfig.builder()
                .targetUrl(URI.create("http://localhost/"))
                .method(HttpMethod.POST)
                .requestBody(body)
                .build();

        body[0] = 9;
        byte[] returnedBody = config.requestBody();
        returnedBody[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, config.requestBody());
    }

    @Test
    void rejectsNonFiniteRate() {
        assertThrows(IllegalArgumentException.class, () -> TestConfig.builder()
                .targetUrl(URI.create("http://localhost/"))
                .targetRate(Double.NaN)
                .build());
    }

    @Test
    void rejectsNonHttpTargets() {
        assertThrows(IllegalArgumentException.class, () -> TestConfig.builder()
                .targetUrl(URI.create("ftp://example.com/file"))
                .build());
    }

    @Test
    void rejectsNegativeThinkTime() {
        assertThrows(IllegalArgumentException.class, () -> TestConfig.builder()
                .targetUrl(URI.create("http://localhost/"))
                .thinkTime(java.time.Duration.ofMillis(-1))
                .build());
    }

    @Test
    void rejectsClosedLoopConcurrencyAboveSafetyLimit() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                TestConfig.builder()
                        .targetUrl(URI.create("http://localhost/"))
                        .concurrency(TestConfig.MAX_CLOSED_LOOP_CONCURRENCY + 1)
                        .build());

        assertEquals("Closed-loop concurrency must be <= "
                + TestConfig.MAX_CLOSED_LOOP_CONCURRENCY + ", got "
                + (TestConfig.MAX_CLOSED_LOOP_CONCURRENCY + 1), exception.getMessage());
    }

    @Test
    void openLoopInFlightLimitIsNotSubjectToClosedLoopConcurrencyCap() {
        assertDoesNotThrow(() -> TestConfig.builder()
                .targetUrl(URI.create("http://localhost/"))
                .targetRate(1.0)
                .concurrency(TestConfig.MAX_CLOSED_LOOP_CONCURRENCY + 1)
                .build());
    }

    @Test
    void acceptsClosedLoopConcurrencyAtSafetyLimit() {
        assertDoesNotThrow(() -> TestConfig.builder()
                .targetUrl(URI.create("http://localhost/"))
                .concurrency(TestConfig.MAX_CLOSED_LOOP_CONCURRENCY)
                .build());
    }
}
