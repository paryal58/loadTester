package com.loadtester.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorShutdownTest {

    @Test
    void reportsFailureWhenWorkersRemainAfterForcedShutdown() {
        NonTerminatingExecutor executor = new NonTerminatingExecutor();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ExecutorShutdown.awaitTermination(executor, 1, TimeUnit.SECONDS));

        assertTrue(executor.forcedShutdown);
        assertEquals(2, executor.awaitCount.get());
        assertTrue(exception.getMessage().contains("refusing to report incomplete results"));
    }

    @Test
    void reportsFailureWhenForcedShutdownStopsWorkers() {
        TerminatingAfterShutdownExecutor executor = new TerminatingAfterShutdownExecutor();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ExecutorShutdown.awaitTermination(executor, 1, TimeUnit.SECONDS));

        assertTrue(executor.forcedShutdown);
        assertEquals(2, executor.awaitCount.get());
        assertTrue(exception.getMessage().contains("outstanding work was interrupted"));
    }

    private static final class NonTerminatingExecutor extends AbstractExecutorService {
        private final AtomicInteger awaitCount = new AtomicInteger();
        private boolean forcedShutdown;

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            forcedShutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return forcedShutdown;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            awaitCount.incrementAndGet();
            return false;
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TerminatingAfterShutdownExecutor extends AbstractExecutorService {
        private final AtomicInteger awaitCount = new AtomicInteger();
        private boolean forcedShutdown;

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            forcedShutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return forcedShutdown;
        }

        @Override
        public boolean isTerminated() {
            return forcedShutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return awaitCount.incrementAndGet() > 1;
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }
}
