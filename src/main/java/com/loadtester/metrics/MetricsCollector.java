package com.loadtester.metrics;

import com.loadtester.http.RequestOutcome;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Aggregates request outcomes into final test metrics.
 */
public final class MetricsCollector {

    private final LatencyHistogram histogram = new LatencyHistogram();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    private final LongAdder droppedRequests = new LongAdder();
    private final LongAdder dispatchDelaySamples = new LongAdder();
    private final ConcurrentHashMap<Integer, LongAdder> statusCodeCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> errorTypeCounts = new ConcurrentHashMap<>();

    private final long testStartNanos;
    private final long warmupEndNanos;

    /** Requests delayed beyond this threshold count as saturation events. */
    private static final long SATURATION_THRESHOLD_NANOS = 50_000_000L; // 50ms

    public MetricsCollector(long testStartNanos, Duration warmupDuration) {
        this.testStartNanos = testStartNanos;
        this.warmupEndNanos = testStartNanos + warmupDuration.toNanos();
    }

    /**
     * Records one completed request and skips warmup samples.
     */
    public void recordOutcome(RequestOutcome outcome) {
        if (outcome.intendedSendTimeNanos() < warmupEndNanos) {
            return; // still warming up - excluded from results by design
        }

        totalRequests.increment();
        histogram.record(outcome.latencyNanos());

        if (outcome.success()) {
            successCount.increment();
        } else {
            errorCount.increment();
            String reason = outcome.errorMessage() != null ? outcome.errorMessage() : "unknown error";
            errorTypeCounts.computeIfAbsent(reason, k -> new LongAdder()).increment();
        }

        statusCodeCounts.computeIfAbsent(outcome.statusCode(), k -> new LongAdder()).increment();

        if (outcome.dispatchDelayNanos() > SATURATION_THRESHOLD_NANOS) {
            dispatchDelaySamples.increment();
        }
    }

    /** Records a scheduled open-loop arrival that could not be dispatched at the in-flight cap. */
    public void recordDropped(long intendedSendTimeNanos) {
        if (intendedSendTimeNanos >= warmupEndNanos) {
            droppedRequests.increment();
        }
    }

    /** Returns a lightweight snapshot for the live console line. */
    public LiveSnapshot liveSnapshot(long nowNanos) {
        long elapsedNanos = Math.max(1, nowNanos - Math.max(testStartNanos, warmupEndNanos));
        long total = totalRequests.sum();
        double rps = total / (elapsedNanos / 1_000_000_000.0);
        return new LiveSnapshot(
                total,
                errorCount.sum(),
                rps,
                histogram.percentile(50),
                histogram.percentile(95)
        );
    }

    /** Builds the final immutable test results. */
    public TestResults finalResults(Duration measurementDuration) {
        long total = totalRequests.sum();
        double seconds = Math.max(measurementDuration.toNanos(), 1) / 1_000_000_000.0;

        return new TestResults(
                total,
                successCount.sum(),
                errorCount.sum(),
                measurementDuration,
                total / seconds,
                histogram.min(),
                histogram.percentile(50),
                histogram.percentile(75),
                histogram.percentile(90),
                histogram.percentile(95),
                histogram.percentile(99),
                histogram.percentile(99.9),
                histogram.max(),
                histogram.mean(),
                snapshotMap(statusCodeCounts),
                snapshotMap(errorTypeCounts),
                dispatchDelaySamples.sum(),
                droppedRequests.sum()
        );
    }

    private static <K> Map<K, Long> snapshotMap(ConcurrentHashMap<K, LongAdder> source) {
        Map<K, Long> result = new java.util.LinkedHashMap<>();
        source.forEach((key, adder) -> result.put(key, adder.sum()));
        return result;
    }

    /** Lightweight live-progress data for the console reporter. */
    public record LiveSnapshot(long totalRequests, long errorCount, double currentRps,
                                long p50Nanos, long p95Nanos) {
    }
}
