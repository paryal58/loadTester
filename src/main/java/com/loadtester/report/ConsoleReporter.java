package com.loadtester.report;

import com.loadtester.config.TestConfig;
import com.loadtester.metrics.MetricsCollector;
import com.loadtester.metrics.TestResults;
import com.loadtester.util.FormatUtils;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.loadtester.util.FormatUtils.*;

/**
 * Prints the startup banner, live progress, and final report.
 */
public final class ConsoleReporter {

    private final MetricsCollector collector;
    private final long measurementStartNanos;
    private final long testEndNanos;
    private ScheduledExecutorService ticker;

    public ConsoleReporter(MetricsCollector collector, long measurementStartNanos, long testEndNanos) {
        this.collector = collector;
        this.measurementStartNanos = measurementStartNanos;
        this.testEndNanos = testEndNanos;
    }

    /** Prints the startup banner for the test. */
    public static void printBanner(TestConfig config) {
        String mode = config.isOpenLoop() ? "OPEN-LOOP (fixed rate)" : "CLOSED-LOOP (fixed concurrency)";
        System.out.println("=".repeat(70));
        System.out.println("Mini Load Tester");
        System.out.println("=".repeat(70));
        System.out.println("Target:       " + config.method() + " " + config.targetUrl());
        System.out.println("Mode:         " + mode);
        if (config.isOpenLoop()) {
            System.out.println("Target rate:  " + config.targetRate() + " req/s");
            System.out.println("Max in-flight:" + " " + config.concurrency());
        } else {
            System.out.println("Concurrency:  " + config.concurrency() + " virtual users");
        }
        System.out.println("Ramp-up:      " + config.rampUpSpec());
        System.out.println("Duration:     " + config.testDuration());
        if (!config.warmupDuration().isZero()) {
            System.out.println("Warmup:       " + config.warmupDuration() + " (excluded from results)");
        }
        System.out.println("=".repeat(70));
        System.out.println();
    }

    /** Starts periodic progress output for the console. */
    public void startLiveUpdates() {
        ThreadFactory daemonFactory = runnable -> {
            Thread t = new Thread(runnable, "console-reporter");
            t.setDaemon(true);
            return t;
        };
        ticker = Executors.newSingleThreadScheduledExecutor(daemonFactory);
        ticker.scheduleAtFixedRate(this::printLiveLine, 1, 1, TimeUnit.SECONDS);
    }

    /** Stops the live-update thread. */
    public void stopLiveUpdates() {
        if (ticker != null) {
            ticker.shutdownNow();
        }
        // Move to a fresh line so the final report doesn't overwrite the last progress line.
        System.out.println();
    }

    private void printLiveLine() {
        long now = System.nanoTime();
        boolean inWarmup = now < measurementStartNanos;
        long elapsedTotalSeconds = (now - (measurementStartNanos)) / 1_000_000_000L;

        if (inWarmup) {
            System.out.print("\r[warming up...]" + " ".repeat(50));
            return;
        }

        MetricsCollector.LiveSnapshot snap = collector.liveSnapshot(now);
        String line = String.format(
                "\r[%4ds] sent=%-8s rps=%-8s errors=%-6s p50=%-8s p95=%-8s",
                Math.max(0, elapsedTotalSeconds),
                withThousandsSeparator(snap.totalRequests()),
                decimal(snap.currentRps(), 1),
                snap.errorCount(),
                nanosToMillisString(snap.p50Nanos()) + "ms",
                nanosToMillisString(snap.p95Nanos()) + "ms"
        );
        System.out.print(line);
    }

    /** Prints the final results table. */
    public static void printFinalReport(TestResults results, TestConfig config) {
        System.out.println("=".repeat(70));
        System.out.println("RESULTS");
        System.out.println("=".repeat(70));

        System.out.printf("%-28s %s%n", "Total requests:", withThousandsSeparator(results.totalRequests()));
        System.out.printf("%-28s %s%n", "Successful:", withThousandsSeparator(results.successCount()));
        System.out.printf("%-28s %s (%s)%n", "Errors:", withThousandsSeparator(results.errorCount()), percentage(results.errorRate()));
        System.out.printf("%-28s %s%n", "Measured duration:", formatDuration(results.actualDuration()));
        System.out.printf("%-28s %s req/s%n", "Throughput:", decimal(results.throughputPerSecond(), 2));

        System.out.println();
        System.out.println("Latency (ms):");
        System.out.printf("  %-8s %10s%n", "min", nanosToMillisString(results.minLatencyNanos()));
        System.out.printf("  %-8s %10s%n", "p50", nanosToMillisString(results.p50LatencyNanos()));
        System.out.printf("  %-8s %10s%n", "p75", nanosToMillisString(results.p75LatencyNanos()));
        System.out.printf("  %-8s %10s%n", "p90", nanosToMillisString(results.p90LatencyNanos()));
        System.out.printf("  %-8s %10s%n", "p95", nanosToMillisString(results.p95LatencyNanos()));
        System.out.printf("  %-8s %10s%n", "p99", nanosToMillisString(results.p99LatencyNanos()));
        System.out.printf("  %-8s %10s%n", "p99.9", nanosToMillisString(results.p999LatencyNanos()));
        System.out.printf("  %-8s %10s%n", "max", nanosToMillisString(results.maxLatencyNanos()));
        System.out.printf("  %-8s %10s%n", "mean", decimal(nanosToMillis((long) results.meanLatencyNanos()), 2));

        System.out.println();
        System.out.println("Status codes:");
        for (Map.Entry<Integer, Long> entry : results.statusCodeCounts().entrySet()) {
            String label = entry.getKey() == -1 ? "no response" : String.valueOf(entry.getKey());
            System.out.printf("  %-14s %s%n", label, withThousandsSeparator(entry.getValue()));
        }

        if (!results.errorTypeCounts().isEmpty()) {
            System.out.println();
            System.out.println("Error breakdown:");
            for (Map.Entry<String, Long> entry : results.errorTypeCounts().entrySet()) {
                System.out.printf("  %-30s %s%n", entry.getKey(), withThousandsSeparator(entry.getValue()));
            }
        }

        if (config.isOpenLoop() && results.dispatchDelaySamples() > 0) {
            System.out.println();
            System.out.println("WARNING: " + withThousandsSeparator(results.dispatchDelaySamples())
                    + " requests were dispatched more than 50ms late.");
            System.out.println("This means the target (or your network) could not sustain the requested rate of "
                    + config.targetRate() + " req/s at a concurrency cap of " + config.concurrency() + ".");
            System.out.println("Consider raising --concurrency (max in-flight cap) or lowering --rate.");
        }

        System.out.println("=".repeat(70));
    }

    private static String formatDuration(java.time.Duration d) {
        double seconds = d.toNanos() / 1_000_000_000.0;
        return decimal(seconds, 2) + "s";
    }
}
