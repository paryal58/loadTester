package com.loadtester.engine;

import com.loadtester.config.TestConfig;
import com.loadtester.http.RequestExecutor;
import com.loadtester.http.RequestOutcome;
import com.loadtester.metrics.MetricsCollector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Runs the open-loop load model by dispatching requests at a target rate.
 */
public final class OpenLoopRunner {

    /** Switch from sleep to a short spin for the last millisecond. */
    private static final long SPIN_THRESHOLD_NANOS = 1_000_000L; // 1ms

    private OpenLoopRunner() {
    }

    public static void run(TestConfig config,
                            RampUpStrategy rampUpStrategy,
                            RequestExecutor executor,
                            MetricsCollector collector,
                            long warmupStartNanos,
                            long measurementStartNanos,
                            long testEndNanos) throws InterruptedException {

        int maxInFlight = config.concurrency();
        Semaphore inFlightPermits = new Semaphore(maxInFlight);

        // A cached pool is fine because the semaphore caps in-flight work.
        ExecutorService dispatchPool = Executors.newCachedThreadPool(daemonThreadFactory("dispatch-worker"));

        // Start scheduling from the warmup start so warmup traffic is real.
        long nextSendTimeNanos = warmupStartNanos;

        while (true) {
            long now = System.nanoTime();
            if (now >= testEndNanos) {
                break;
            }

            sleepUntil(nextSendTimeNanos);

            long intendedSendTime = nextSendTimeNanos;

            // Keep the warmup rate at the starting ramp value.
            double currentRate;
            if (intendedSendTime < measurementStartNanos) {
                currentRate = Math.max(rampUpStrategy.getTarget(0), 0.01);
            } else {
                long elapsedMillis = (intendedSendTime - measurementStartNanos) / 1_000_000;
                currentRate = Math.max(rampUpStrategy.getTarget(elapsedMillis), 0.01);
            }
            long intervalNanos = (long) (1_000_000_000.0 / currentRate);
            nextSendTimeNanos += intervalNanos;

            // Acquire a permit before dispatching so overload shows up in the metrics.
            long acquireStartNanos = System.nanoTime();
            inFlightPermits.acquire();
            long schedulerStallNanos = System.nanoTime() - acquireStartNanos;
            if (schedulerStallNanos > 1_000_000L) {
                // The scheduler stalled waiting for capacity; the target may be saturated.
            }

            dispatchPool.submit(() -> {
                try {
                    RequestOutcome outcome = executor.execute(intendedSendTime);
                    collector.recordOutcome(outcome);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlightPermits.release();
                }
            });
        }

        dispatchPool.shutdown();
        boolean finished = dispatchPool.awaitTermination(
                config.requestTimeout().toSeconds() + 5, TimeUnit.SECONDS);
        if (!finished) {
            dispatchPool.shutdownNow();
        }
    }

    /**
     * Waits until the next scheduled send time.
     */
    private static void sleepUntil(long targetNanoTime) throws InterruptedException {
        long remaining = targetNanoTime - System.nanoTime();
        if (remaining <= 0) {
            return;
        }

        long coarseWaitNanos = remaining - SPIN_THRESHOLD_NANOS;
        if (coarseWaitNanos > 0) {
            long millis = coarseWaitNanos / 1_000_000L;
            int nanos = (int) (coarseWaitNanos % 1_000_000L);
            Thread.sleep(millis, nanos);
        }

        // Use a short spin for the last millisecond to reduce scheduling jitter.
        while (System.nanoTime() < targetNanoTime) {
            LockSupport.parkNanos(10_000); // 10 microseconds between checks
        }
    }

    private static ThreadFactory daemonThreadFactory(String namePrefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable -> {
            Thread t = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
