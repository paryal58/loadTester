package com.loadtester.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedDaemonThreads {

    private NamedDaemonThreads() {
    }

    public static ExecutorService newCachedThreadPool(String namePrefix) {
        return Executors.newCachedThreadPool(daemonFactory(namePrefix));
    }

    public static ExecutorService newFixedThreadPool(int threads, String namePrefix) {
        return Executors.newFixedThreadPool(threads, daemonFactory(namePrefix));
    }

    public static ExecutorService newSingleThreadExecutor(String namePrefix) {
        return Executors.newSingleThreadExecutor(daemonFactory(namePrefix));
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(String namePrefix) {
        return Executors.newSingleThreadScheduledExecutor(daemonFactory(namePrefix));
    }

    public static ThreadFactory daemonFactory(String namePrefix) {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
