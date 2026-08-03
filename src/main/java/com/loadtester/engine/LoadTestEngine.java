package com.loadtester.engine;

import com.loadtester.config.TestConfig;
import com.loadtester.http.RequestExecutor;
import com.loadtester.metrics.MetricsCollector;
import com.loadtester.metrics.TestResults;
import com.loadtester.report.ConsoleReporter;
import com.loadtester.report.ReportExporter;

import java.time.Duration;

/**
 * Top-level orchestrator that ties together configuration, the HTTP
 * executor, the chosen load-generation strategy (open- or closed-loop),
 * live console reporting, and final report export.
 *
 * <p>This is intentionally a thin coordination layer - all of the actual
 * interesting logic lives in {@link ClosedLoopRunner}, {@link OpenLoopRunner},
 * {@link RampUpStrategy}, and the {@code metrics}/{@code report} packages.
 * Keeping this class thin makes the overall control flow easy to read in
 * one place: build config -> pick a runner -> run -> report.
 */
public final class LoadTestEngine {

    private final TestConfig config;

    public LoadTestEngine(TestConfig config) {
        this.config = config;
    }

    /**
     * Runs the configured load test end-to-end: warmup + measured duration,
     * live progress printing, and a final statistics report (printed to the
     * console and optionally exported to a file).
     */
    public void run() throws InterruptedException {
        RampUpStrategy rampUpStrategy = RampUpStrategy.parse(config.rampUpSpec(), config.peakLoadValue());
        RequestExecutor executor = new RequestExecutor(config);

        long testStartNanos = System.nanoTime();
        long warmupNanos = config.warmupDuration().toNanos();
        long measuredDurationNanos = config.testDuration().toNanos();
        long testEndNanos = testStartNanos + warmupNanos + measuredDurationNanos;

        MetricsCollector collector = new MetricsCollector(testStartNanos, config.warmupDuration());

        ConsoleReporter.printBanner(config);
        ConsoleReporter liveReporter = new ConsoleReporter(collector, testStartNanos + warmupNanos, testEndNanos);
        liveReporter.startLiveUpdates();

        try {
            if (config.isOpenLoop()) {
                OpenLoopRunner.run(config, rampUpStrategy, executor, collector, testStartNanos, testStartNanos + warmupNanos, testEndNanos);
            } else {
                ClosedLoopRunner.run(config, rampUpStrategy, executor, collector, testStartNanos + warmupNanos, testEndNanos);
            }
        } finally {
            liveReporter.stopLiveUpdates();
        }

        Duration actualMeasuredDuration = Duration.ofNanos(System.nanoTime() - (testStartNanos + warmupNanos));
        TestResults results = collector.finalResults(actualMeasuredDuration);

        ConsoleReporter.printFinalReport(results, config);

        if (config.outputFile() != null) {
            ReportExporter.export(results, config.outputFile());
            System.out.println("\nReport written to: " + config.outputFile());
        }
    }
}
