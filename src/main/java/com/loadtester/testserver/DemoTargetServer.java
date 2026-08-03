package com.loadtester.testserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A small demo HTTP server for exercising the load tester locally.
 */
public final class DemoTargetServer {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        int baseLatencyMs = 20;
        int jitterMs = 10;
        double errorRate = 0.0;
        int maxConcurrent = Integer.MAX_VALUE; // unlimited by default

        for (int i = 0; i < args.length - 1; i += 2) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--latency-ms" -> baseLatencyMs = Integer.parseInt(args[i + 1]);
                case "--jitter-ms" -> jitterMs = Integer.parseInt(args[i + 1]);
                case "--error-rate" -> errorRate = Double.parseDouble(args[i + 1]);
                case "--max-concurrent" -> maxConcurrent = Integer.parseInt(args[i + 1]);
                default -> System.err.println("Unknown flag: " + args[i] + " (ignored)");
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new SimulatedHandler(baseLatencyMs, jitterMs, errorRate, maxConcurrent));

        // Use a cached pool so the server can queue work under load.
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.printf(
                "Demo target server listening on http://localhost:%d/  " +
                        "(latency=%dms +/-%dms jitter, errorRate=%.1f%%, maxConcurrent=%s)%n",
                port, baseLatencyMs, jitterMs, errorRate * 100,
                maxConcurrent == Integer.MAX_VALUE ? "unlimited" : String.valueOf(maxConcurrent));
        System.out.println("Press Ctrl+C to stop.");
    }

    /** Simulates request processing, errors, and overload. */
    private static final class SimulatedHandler implements com.sun.net.httpserver.HttpHandler {
        private final int baseLatencyMs;
        private final int jitterMs;
        private final double errorRate;
        private final java.util.concurrent.Semaphore concurrencyGate;

        SimulatedHandler(int baseLatencyMs, int jitterMs, double errorRate, int maxConcurrent) {
            this.baseLatencyMs = baseLatencyMs;
            this.jitterMs = jitterMs;
            this.errorRate = errorRate;
            this.concurrencyGate = new java.util.concurrent.Semaphore(maxConcurrent);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Block when the concurrency cap is reached to simulate queueing.
                concurrencyGate.acquire();
                try {
                    simulateProcessing(exchange);
                } finally {
                    concurrencyGate.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        private void simulateProcessing(HttpExchange exchange) throws IOException {
            int jitter = jitterMs > 0 ? ThreadLocalRandom.current().nextInt(-jitterMs, jitterMs + 1) : 0;
            int sleepMs = Math.max(0, baseLatencyMs + jitter);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            boolean simulatedError = errorRate > 0 && ThreadLocalRandom.current().nextDouble() < errorRate;
            byte[] body = simulatedError
                    ? "{\"error\":\"simulated failure\"}".getBytes()
                    : "{\"status\":\"ok\"}".getBytes();

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(simulatedError ? 500 : 200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
