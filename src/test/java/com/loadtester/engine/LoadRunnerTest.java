package com.loadtester.engine;

import com.loadtester.config.TestConfig;
import com.loadtester.http.RequestExecutor;
import com.loadtester.metrics.MetricsCollector;
import com.loadtester.metrics.TestResults;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadRunnerTest {

    @Test
    void openLoopSkipsArrivalsAtTheInFlightLimitWithoutExceedingIt() throws Exception {
        try (DemoServer server = new DemoServer(100)) {
            TestConfig config = TestConfig.builder()
                    .targetUrl(server.url())
                    .targetRate(100.0)
                    .concurrency(1)
                    .testDuration(Duration.ofMillis(250))
                    .requestTimeout(Duration.ofSeconds(1))
                    .build();
            RequestExecutor executor = new RequestExecutor(config);
            RampUpStrategy strategy = RampUpStrategy.parse("constant", 100);
            long startNanos = System.nanoTime();
            long endNanos = startNanos + config.testDuration().toNanos();
            MetricsCollector collector = new MetricsCollector(startNanos, Duration.ZERO);

            OpenLoopRunner.run(config, strategy, executor,
                    collector, startNanos, startNanos, endNanos);

            TestResults results = collector.finalResults(config.testDuration());
            assertTrue(results.totalRequests() > 0,
                    () -> "Expected completed requests; results=" + results
                            + ", serverRequests=" + server.requestCount.get()
                            + ", start=" + startNanos + ", end=" + endNanos
                            + ", now=" + System.nanoTime());
            assertTrue(results.droppedRequests() > 5,
                    () -> "Expected saturated arrivals to be dropped; results=" + results
                            + ", serverRequests=" + server.requestCount.get());
            assertEquals(1, server.maxActive.get());
        }
    }

    @Test
    void closedLoopSendsWarmupTrafficButExcludesItFromMetrics() throws Exception {
        try (DemoServer server = new DemoServer(10)) {
            TestConfig config = TestConfig.builder()
                    .targetUrl(server.url())
                    .concurrency(2)
                    .warmupDuration(Duration.ofMillis(150))
                    .testDuration(Duration.ofMillis(250))
                    .requestTimeout(Duration.ofSeconds(1))
                    .build();
            RequestExecutor executor = new RequestExecutor(config);
            RampUpStrategy strategy = RampUpStrategy.parse("constant", config.concurrency());
            long startNanos = System.nanoTime();
            long measurementStartNanos = startNanos + config.warmupDuration().toNanos();
            long endNanos = measurementStartNanos + config.testDuration().toNanos();
            MetricsCollector collector = new MetricsCollector(startNanos, config.warmupDuration());

            ClosedLoopRunner.run(config, strategy,
                    executor, collector, startNanos, measurementStartNanos, endNanos);

            assertTrue(server.requestCount.get() > collector.finalResults(config.testDuration()).totalRequests());
            assertTrue(collector.finalResults(config.testDuration()).totalRequests() > 0);
        }
    }

    private static final class DemoServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxActive = new AtomicInteger();
        private final int latencyMillis;

        private DemoServer(int latencyMillis) throws IOException {
            this.latencyMillis = latencyMillis;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                requestCount.incrementAndGet();
                int concurrent = active.incrementAndGet();
                maxActive.accumulateAndGet(concurrent, Math::max);
                try {
                    Thread.sleep(this.latencyMillis);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    active.decrementAndGet();
                    exchange.close();
                }
            });
            server.start();
        }

        private URI url() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
