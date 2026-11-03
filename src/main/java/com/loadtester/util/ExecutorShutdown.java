package com.loadtester.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Ensures worker tasks have stopped before their results are consumed. */
public final class ExecutorShutdown {

    private static final long FORCED_SHUTDOWN_WAIT_SECONDS = 5;

    private ExecutorShutdown() {
    }

    /**
     * Waits for graceful termination, then interrupts remaining tasks and confirms termination.
     *
     * @throws IllegalStateException if graceful shutdown times out and work may be incomplete
     */
    public static void awaitTermination(ExecutorService executor, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (executor.awaitTermination(timeout, unit)) {
            return;
        }

        executor.shutdownNow();
        if (!executor.awaitTermination(FORCED_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Worker threads did not stop after forced shutdown; refusing to report incomplete results");
        }
        throw new IllegalStateException(
                "Worker shutdown timed out; outstanding work was interrupted, refusing to report incomplete results");
    }
}
