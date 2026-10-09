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
 * @param actualDuration        configured measurement-window duration
 * @param throughputPerSecond    totalRequests / actualDuration in seconds
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
 * @param dispatchDelaySamples   count of open-loop requests started more than 50ms after their scheduled time
 * @param droppedRequests        scheduled open-loop arrivals skipped because the in-flight cap was full
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
        long dispatchDelaySamples,
        long droppedRequests
) {

    public double errorRate() {
        return totalRequests == 0 ? 0.0 : (double) errorCount / totalRequests;
    }
}
