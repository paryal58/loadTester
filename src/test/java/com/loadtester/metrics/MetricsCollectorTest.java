package com.loadtester.metrics;

import com.loadtester.http.RequestOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsCollectorTest {

    @Test
    void excludesWarmupArrivalsEvenWhenTheyFinishDuringMeasurement() {
        MetricsCollector collector = new MetricsCollector(1_000L, Duration.ofNanos(100));

        collector.recordOutcome(outcome(1_099L, 1_500L));
        collector.recordDropped(1_099L);
        collector.recordOutcome(outcome(1_100L, 60_000_000L, 60_000_100L));
        collector.recordDropped(1_100L);

        TestResults results = collector.finalResults(Duration.ofSeconds(1));
        MetricsCollector.LiveSnapshot snapshot = collector.liveSnapshot(1_000_000_000L);

        assertEquals(1L, results.totalRequests());
        assertEquals(1L, results.droppedRequests());
        assertEquals(1L, results.successCount());
        assertEquals(1L, snapshot.droppedRequests());
        assertEquals(1L, snapshot.dispatchDelaySamples());
    }

    private static RequestOutcome outcome(long intendedSendTimeNanos, long actualEndNanos) {
        return outcome(intendedSendTimeNanos, intendedSendTimeNanos, actualEndNanos);
    }

    private static RequestOutcome outcome(long intendedSendTimeNanos, long actualStartNanos,
                                          long actualEndNanos) {
        return new RequestOutcome(
                true, 200, intendedSendTimeNanos, actualStartNanos, actualEndNanos,
                actualEndNanos - intendedSendTimeNanos, null);
    }
}
