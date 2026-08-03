package com.loadtester.metrics;

import java.time.Duration;
import java.util.Map;

/**
 * Immutable snapshot of a completed load test's final statistics. This is
 * the object that both {@code ConsoleReporter} and {@code ReportExporter}
 * render - keeping it as a plain data record means reporting logic never
 * has to reach back into the (mutable, concurrent) {@link MetricsCollector}.
 *
 * @param totalRequests        total requests recorded (post-warmup)
 * @param successCount         requests with status < 400 and no transport error
 * @param errorCount           requests that failed (non-2xx/3xx or exception)
 * @param actualDuration        wall-clock duration over which totalRequests were recorded
 * @param throughputPerSecond    totalRequests / actualDuration.toSeconds() (as a double for sub-second precision)
 * @param minLatencyNanos        fastest recorded request
 * @param p50LatencyNanos        median latency
 * @param p75LatencyNanos        75th percentile latency
 * @param p90LatencyNanos        90th percentile latency
 * @param p95LatencyNanos        95th percentile latency
 * @param p99LatencyNanos        99th percentile latency
 * @param p999LatencyNanos       99.9th percentile latency
 * @param maxLatencyNanos        slowest recorded request
 * @param meanLatencyNanos       arithmetic mean latency
 * @param statusCodeCounts       count of requests per HTTP status code (-1 = no response received)
 * @param errorTypeCounts        count of requests per distinct error message/reason
 * @param dispatchDelaySamples   count of requests where the scheduler could not dispatch on time
 *                               (open-loop mode only; indicates the target could not sustain the
 *                               requested rate - see {@code OpenLoopRunner})
 */
public record TestResults(
        long totalRequests,
        long successCount,
        long errorCount,
        Duration actualDuration,
        double throughputPerSecond,
        long minLatencyNanos,
        long p50LatencyNanos,
        long p75LatencyNanos,
        long p90LatencyNanos,
        long p95LatencyNanos,
        long p99LatencyNanos,
        long p999LatencyNanos,
        long maxLatencyNanos,
        double meanLatencyNanos,
        Map<Integer, Long> statusCodeCounts,
        Map<String, Long> errorTypeCounts,
        long dispatchDelaySamples
) {

    public double errorRate() {
        return totalRequests == 0 ? 0.0 : (double) errorCount / totalRequests;
    }
}
