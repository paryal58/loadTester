package com.loadtester.http;

import com.loadtester.config.TestConfig;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.net.http.HttpTimeoutException;

/**
 * Sends individual HTTP requests with a shared HttpClient.
 */
public final class RequestExecutor {

    private final HttpClient httpClient;
    private final TestConfig config;
    private final byte[] requestBody;

    public RequestExecutor(TestConfig config) {
        this.config = config;
        this.requestBody = config.requestBody();
        this.httpClient = HttpClient.newBuilder()
                // Prefer HTTP/2 when the server supports it.
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(config.requestTimeout())
                .build();
    }

    /**
     * Executes one HTTP request and returns the outcome.
     */
    public RequestOutcome execute(long intendedSendTimeNanos) throws InterruptedException {
        HttpRequest request = buildRequest();
        long actualStartNanos = System.nanoTime();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long actualEndNanos = System.nanoTime();
            int status = response.statusCode();
            boolean success = status < 400;

            return new RequestOutcome(
                    success,
                    status,
                    intendedSendTimeNanos,
                    actualStartNanos,
                    actualEndNanos,
                    actualEndNanos - intendedSendTimeNanos,
                    success ? null : "HTTP " + status
            );

        } catch (HttpTimeoutException e) {
            return failureOutcome(intendedSendTimeNanos, actualStartNanos, "timeout");
        } catch (ConnectException e) {
            return failureOutcome(intendedSendTimeNanos, actualStartNanos, "connection refused");
        } catch (IOException e) {
            return failureOutcome(intendedSendTimeNanos, actualStartNanos,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private RequestOutcome failureOutcome(long intendedSendTimeNanos, long actualStartNanos, String reason) {
        long actualEndNanos = System.nanoTime();
        return new RequestOutcome(
                false,
                -1,
                intendedSendTimeNanos,
                actualStartNanos,
                actualEndNanos,
                actualEndNanos - intendedSendTimeNanos,
                reason
        );
    }

    private HttpRequest buildRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(config.targetUrl())
                .timeout(config.requestTimeout());

        for (Map.Entry<String, String> header : config.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        HttpRequest.BodyPublisher bodyPublisher = config.method().supportsBody() && requestBody.length > 0
                ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
                : HttpRequest.BodyPublishers.noBody();

        builder.method(config.method().name(), bodyPublisher);

        return builder.build();
    }
}
