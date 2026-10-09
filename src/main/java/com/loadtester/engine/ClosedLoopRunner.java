package com.loadtester.engine;

import com.loadtester.config.TestConfig;
import com.loadtester.http.RequestExecutor;
import com.loadtester.http.RequestOutcome;
import com.loadtester.metrics.MetricsCollector;
import com.loadtester.util.NamedDaemonThreads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the closed-loop load model with a fixed pool of virtual users.
 */
public final class ClosedLoopRunner {

    private static final long IDLE_POLL_MILLIS = 50;

    private ClosedLoopRunner() {
    }

    public static void run(TestConfig config,
                            RampUpStrategy rampUpStrategy,
                            RequestExecutor executor,
                            MetricsCollector collector,
                            long warmupStartNanos,
                            long measurementStartNanos,
                            long testEndNanos) throws InterruptedException {

        int maxUsers = config.concurrency();
        AtomicInteger currentAllowedConcurrency = new AtomicInteger(0);
        currentAllowedConcurrency.set(boundedConcurrency(rampUpStrategy.getTarget(0), maxUsers));

        // Update the active worker count from the ramp-up curve every 100ms.
        ExecutorService rampTicker = NamedDaemonThreads.newSingleThreadExecutor("ramp-ticker");
        ExecutorService workerPool = NamedDaemonThreads.newFixedThreadPool(maxUsers, "vu-worker");
        rampTicker.submit(() -> {
            try {
                while (System.nanoTime() < testEndNanos && !Thread.currentThread().isInterrupted()) {
                    long now = System.nanoTime();
                    long elapsedMillis = now < measurementStartNanos
                            ? 0
                            : (now - measurementStartNanos) / 1_000_000;
                    currentAllowedConcurrency.set(
                            boundedConcurrency(rampUpStrategy.getTarget(elapsedMillis), maxUsers));
                    Thread.sleep(100);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            for (int workerId = 0; workerId < maxUsers; workerId++) {
                final int id = workerId;
                workerPool.submit(() -> runVirtualUser(
                        id, currentAllowedConcurrency, config, executor, collector, warmupStartNanos, testEndNanos));
            }

            workerPool.shutdown();
            workerPool.awaitTermination(config.requestTimeout().toSeconds() + 5, TimeUnit.SECONDS);
        } finally {
            workerPool.shutdownNow();
            rampTicker.shutdownNow();
        }
    }

    private static void runVirtualUser(int workerId,
                                        AtomicInteger currentAllowedConcurrency,
                                        TestConfig config,
                                        RequestExecutor executor,
                                        MetricsCollector collector,
                                        long warmupStartNanos,
                                        long testEndNanos) {
        try {
            while (System.nanoTime() < warmupStartNanos) {
                Thread.sleep(IDLE_POLL_MILLIS);
            }
            while (System.nanoTime() < testEndNanos) {
                if (workerId >= currentAllowedConcurrency.get()) {
                    // Wait until the ramp-up curve admits this worker.
                    Thread.sleep(IDLE_POLL_MILLIS);
                    continue;
                }

                // Send immediately when the worker is allowed to run.
                long now = System.nanoTime();
                RequestOutcome outcome = executor.execute(now);
                collector.recordOutcome(outcome);

                if (!config.thinkTime().isZero()) {
                    long thinkTimeNanos = config.thinkTime().toNanos();
                    Thread.sleep(thinkTimeNanos / 1_000_000L, (int) (thinkTimeNanos % 1_000_000L));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int boundedConcurrency(double requestedUsers, int maximumUsers) {
        return (int) Math.min(Math.max(Math.round(requestedUsers), 0), maximumUsers);
    }

}
