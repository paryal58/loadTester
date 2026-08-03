package com.loadtester.engine;

import com.loadtester.config.TestConfig;
import com.loadtester.http.RequestExecutor;
import com.loadtester.http.RequestOutcome;
import com.loadtester.metrics.MetricsCollector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
                            long testStartNanos,
                            long testEndNanos) throws InterruptedException {

        int maxUsers = config.concurrency();
        AtomicInteger currentAllowedConcurrency = new AtomicInteger(0);

        // Update the active worker count from the ramp-up curve every 100ms.
        ExecutorService rampTicker = Executors.newSingleThreadExecutor(daemonThreadFactory("ramp-ticker"));
        rampTicker.submit(() -> {
            try {
                while (System.nanoTime() < testEndNanos && !Thread.currentThread().isInterrupted()) {
                    long elapsedMillis = (System.nanoTime() - testStartNanos) / 1_000_000;
                    int target = (int) Math.round(rampUpStrategy.getTarget(elapsedMillis));
                    currentAllowedConcurrency.set(Math.min(Math.max(target, 0), maxUsers));
                    Thread.sleep(100);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        ExecutorService workerPool = Executors.newFixedThreadPool(maxUsers, daemonThreadFactory("vu-worker"));
        for (int workerId = 0; workerId < maxUsers; workerId++) {
            final int id = workerId;
            workerPool.submit(() -> runVirtualUser(id, currentAllowedConcurrency, config, executor, collector, testEndNanos));
        }

        workerPool.shutdown();
        // Let in-flight requests finish before shutdown.
        boolean finished = workerPool.awaitTermination(
                config.requestTimeout().toSeconds() + 5, TimeUnit.SECONDS);
        if (!finished) {
            workerPool.shutdownNow();
        }

        rampTicker.shutdownNow();
    }

    private static void runVirtualUser(int workerId,
                                        AtomicInteger currentAllowedConcurrency,
                                        TestConfig config,
                                        RequestExecutor executor,
                                        MetricsCollector collector,
                                        long testEndNanos) {
        try {
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
                    Thread.sleep(config.thinkTime().toMillis());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static java.util.concurrent.ThreadFactory daemonThreadFactory(String namePrefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable -> {
            Thread t = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
