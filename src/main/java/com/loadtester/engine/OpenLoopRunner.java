package com.loadtester.engine;

import com.loadtester.config.TestConfig;
import com.loadtester.http.RequestExecutor;
import com.loadtester.http.RequestOutcome;
import com.loadtester.metrics.MetricsCollector;
import com.loadtester.util.NamedDaemonThreads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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

        // The scheduler never waits for a permit; arrivals that exceed the cap are recorded as dropped.
        ExecutorService dispatchPool = NamedDaemonThreads.newCachedThreadPool("dispatch-worker");

        // Start scheduling from the warmup start so warmup traffic is real.
        long nextSendTimeNanos = warmupStartNanos;

        try {
            while (true) {
                long now = System.nanoTime();
                if (now >= testEndNanos) {
                    break;
                }

                sleepUntil(nextSendTimeNanos);
                if (System.nanoTime() >= testEndNanos) {
                    break;
                }

                long intendedSendTime = nextSendTimeNanos;

                double currentRate;
                if (intendedSendTime < measurementStartNanos) {
                    currentRate = rampUpStrategy.getTarget(0);
                } else {
                    long elapsedMillis = (intendedSendTime - measurementStartNanos) / 1_000_000;
                    currentRate = rampUpStrategy.getTarget(elapsedMillis);
                }

                if (currentRate <= 0) {
                    nextSendTimeNanos = System.nanoTime() + 1_000_000L;
                    continue;
                }
                long intervalNanos = Math.max(1L, (long) (1_000_000_000.0 / currentRate));
                nextSendTimeNanos += intervalNanos;

                if (!inFlightPermits.tryAcquire()) {
                    collector.recordDropped(intendedSendTime);
                    continue;
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
        } catch (InterruptedException e) {
            dispatchPool.shutdownNow();
            throw e;
        } catch (RuntimeException e) {
            dispatchPool.shutdownNow();
            throw e;
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

}
