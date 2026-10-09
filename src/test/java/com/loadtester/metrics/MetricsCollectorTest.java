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
        collector.recordOutcome(outcome(1_100L, 1_200L));
        collector.recordDropped(1_100L);

        TestResults results = collector.finalResults(Duration.ofSeconds(1));

        assertEquals(1L, results.totalRequests());
        assertEquals(1L, results.droppedRequests());
        assertEquals(1L, results.successCount());
    }

    private static RequestOutcome outcome(long intendedSendTimeNanos, long actualEndNanos) {
        return new RequestOutcome(
                true, 200, intendedSendTimeNanos, intendedSendTimeNanos, actualEndNanos,
                actualEndNanos - intendedSendTimeNanos, null);
    }
}
