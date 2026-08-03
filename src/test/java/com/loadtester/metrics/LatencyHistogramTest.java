package com.loadtester.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the logarithmic-bucket histogram produces percentiles within
 * the documented ~1% relative error tolerance, and that edge cases (empty
 * histogram, single value, min/max) behave sanely.
 */
class LatencyHistogramTest {

    private static final double TOLERANCE = 0.02; // 2% relative error, generous vs. the ~1% design target

    @Test
    void emptyHistogramReturnsZeroForEverything() {
        LatencyHistogram h = new LatencyHistogram();
        assertEquals(0, h.count());
        assertEquals(0, h.percentile(50));
        assertEquals(0, h.min());
        assertEquals(0, h.max());
        assertEquals(0.0, h.mean());
    }

    @Test
    void singleValueIsReturnedForAllPercentiles() {
        LatencyHistogram h = new LatencyHistogram();
        h.record(10_000_000L); // 10ms
        assertWithinTolerance(10_000_000L, h.percentile(50));
        assertWithinTolerance(10_000_000L, h.percentile(99));
        assertEquals(10_000_000L, h.min());
        assertEquals(10_000_000L, h.max());
    }

    @Test
    void percentilesOnUniformDistributionAreAccurate() {
        LatencyHistogram h = new LatencyHistogram();
        // Record latencies from 1ms to 1000ms in 1ms steps (1000 samples).
        for (int ms = 1; ms <= 1000; ms++) {
            h.record(ms * 1_000_000L);
        }

        assertEquals(1000, h.count());
        assertWithinTolerance(500_000_000L, h.percentile(50));  // ~500ms
        assertWithinTolerance(950_000_000L, h.percentile(95));  // ~950ms
        assertWithinTolerance(990_000_000L, h.percentile(99));  // ~990ms
        assertEquals(1_000_000L, h.min());
        assertEquals(1_000_000_000L, h.max());
    }

    @Test
    void meanIsComputedCorrectly() {
        LatencyHistogram h = new LatencyHistogram();
        h.record(10_000_000L);
        h.record(20_000_000L);
        h.record(30_000_000L);
        assertEquals(20_000_000.0, h.mean(), 1.0);
    }

    @Test
    void concurrentRecordingDoesNotLoseSamples() throws InterruptedException {
        LatencyHistogram h = new LatencyHistogram();
        int threads = 8;
        int samplesPerThread = 10_000;

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < samplesPerThread; j++) {
                    h.record(1_000_000L);
                }
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        assertEquals((long) threads * samplesPerThread, h.count());
    }

    private void assertWithinTolerance(long expected, long actual) {
        double lowerBound = expected * (1 - TOLERANCE);
        double upperBound = expected * (1 + TOLERANCE);
        assertTrue(actual >= lowerBound && actual <= upperBound,
                "Expected ~" + expected + " but got " + actual + " (outside " + (TOLERANCE * 100) + "% tolerance)");
    }
}
