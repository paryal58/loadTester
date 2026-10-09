package com.loadtester.config;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, fully-validated configuration for a single load test run.
 *
 * <p>This object is the single source of truth passed down through the
 * engine, HTTP executor, and reporters. Building it goes through
 * {@link Builder#build()}, which performs all cross-field validation in one
 * place (e.g. "rate mode requires a positive rate", "concurrency must be
 * >= 1") so that by the time a {@code TestConfig} exists, every consumer
 * can trust it is internally consistent and does not need to re-validate.
 *
 * <p>Two load models are supported, distinguished by whether
 * {@link #targetRate()} is present:
 * <ul>
 *   <li><b>Closed-loop (concurrency mode)</b>: {@code targetRate} is empty.
 *       {@link #concurrency()} virtual users each loop
 *       send-wait-for-response-repeat. Simple and intuitive, but subject to
 *       <i>coordinated omission</i> - see {@code OpenLoopRunner} javadoc.</li>
 *   <li><b>Open-loop (rate mode)</b>: {@code targetRate} is present. Requests
 *       are dispatched on a fixed schedule regardless of how long previous
 *       requests take, which is the statistically correct way to measure
 *       tail latency under load. {@link #concurrency()} is reused here as
 *       the max-in-flight cap (a safety valve against unbounded resource
 *       growth if the target can't keep up).</li>
 * </ul>
 */
public final class TestConfig {

    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private final URI targetUrl;
    private final HttpMethod method;
    private final Map<String, String> headers;
    private final byte[] requestBody;
    private final int concurrency;
    private final Double targetRate;          // null => closed-loop mode
    private final String rampUpSpec;          // raw spec, e.g. "linear:0:30"
    private final Duration testDuration;
    private final Duration warmupDuration;
    private final Duration requestTimeout;
    private final Duration thinkTime;
    private final Path outputFile;            // nullable

    private TestConfig(Builder b) {
        this.targetUrl = b.targetUrl;
        this.method = b.method;
        this.headers = Map.copyOf(b.headers);
        this.requestBody = b.requestBody.clone();
        this.concurrency = b.concurrency;
        this.targetRate = b.targetRate;
        this.rampUpSpec = b.rampUpSpec;
        this.testDuration = b.testDuration;
        this.warmupDuration = b.warmupDuration;
        this.requestTimeout = b.requestTimeout;
        this.thinkTime = b.thinkTime;
        this.outputFile = b.outputFile;
    }

    public URI targetUrl() { return targetUrl; }
    public HttpMethod method() { return method; }
    public Map<String, String> headers() { return headers; }
    public byte[] requestBody() { return requestBody.clone(); }
    public int concurrency() { return concurrency; }
    public Double targetRate() { return targetRate; }
    public boolean isOpenLoop() { return targetRate != null; }
    public String rampUpSpec() { return rampUpSpec; }
    public Duration testDuration() { return testDuration; }
    public Duration warmupDuration() { return warmupDuration; }
    public Duration requestTimeout() { return requestTimeout; }
    public Duration thinkTime() { return thinkTime; }
    public Path outputFile() { return outputFile; }

    /** The peak value the ramp-up strategy should climb toward: rate if open-loop, else concurrency. */
    public double peakLoadValue() {
        return isOpenLoop() ? targetRate : concurrency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private URI targetUrl;
        private HttpMethod method = HttpMethod.GET;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] requestBody = new byte[0];
        private int concurrency = 10;
        private Double targetRate = null;
        private String rampUpSpec = "constant";
        private Duration testDuration = Duration.ofSeconds(30);
        private Duration warmupDuration = Duration.ZERO;
        private Duration requestTimeout = Duration.ofSeconds(10);
        private Duration thinkTime = Duration.ZERO;
        private Path outputFile = null;

        public Builder targetUrl(URI targetUrl) { this.targetUrl = targetUrl; return this; }
        public Builder method(HttpMethod method) { this.method = method; return this; }
        public Builder addHeader(String key, String value) {
            this.headers.put(Objects.requireNonNull(key, "Header name cannot be null"),
                    Objects.requireNonNull(value, "Header value cannot be null"));
            return this;
        }
        public Builder requestBody(byte[] body) {
            this.requestBody = Objects.requireNonNull(body, "Request body cannot be null").clone();
            return this;
        }
        public Builder concurrency(int concurrency) { this.concurrency = concurrency; return this; }
        public Builder targetRate(Double targetRate) { this.targetRate = targetRate; return this; }
        public Builder rampUpSpec(String spec) { this.rampUpSpec = spec; return this; }
        public Builder testDuration(Duration d) { this.testDuration = d; return this; }
        public Builder warmupDuration(Duration d) { this.warmupDuration = d; return this; }
        public Builder requestTimeout(Duration d) { this.requestTimeout = d; return this; }
        public Builder thinkTime(Duration d) { this.thinkTime = d; return this; }
        public Builder outputFile(Path p) { this.outputFile = p; return this; }

        public TestConfig build() {
            Objects.requireNonNull(targetUrl, "Target URL is required (--url)");
            Objects.requireNonNull(method, "HTTP method is required");
            Objects.requireNonNull(testDuration, "Test duration is required");
            Objects.requireNonNull(warmupDuration, "Warmup duration is required");
            Objects.requireNonNull(requestTimeout, "Request timeout is required");
            Objects.requireNonNull(thinkTime, "Think time is required");

            String scheme = targetUrl.getScheme();
            if (!targetUrl.isAbsolute() || targetUrl.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Target URL must be an absolute HTTP or HTTPS URL");
            }

            if (concurrency < 1) {
                throw new IllegalArgumentException("Concurrency must be >= 1, got " + concurrency);
            }
            if (targetRate != null && (!Double.isFinite(targetRate) || targetRate <= 0)) {
                throw new IllegalArgumentException("Rate must be > 0, got " + targetRate);
            }
            if (testDuration.isZero() || testDuration.isNegative()) {
                throw new IllegalArgumentException("Test duration must be positive");
            }
            if (warmupDuration.isNegative()) {
                throw new IllegalArgumentException("Warmup duration cannot be negative");
            }
            if (requestTimeout.isZero() || requestTimeout.isNegative()) {
                throw new IllegalArgumentException("Request timeout must be positive");
            }
            if (thinkTime.isNegative()) {
                throw new IllegalArgumentException("Think time cannot be negative");
            }
            try {
                Math.addExact(warmupDuration.toNanos(), testDuration.toNanos());
                requestTimeout.toNanos();
                thinkTime.toNanos();
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Durations are too large to represent in nanoseconds", e);
            }
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!HEADER_NAME.matcher(header.getKey()).matches()
                        || header.getValue().chars().anyMatch(c -> (c < 0x20 && c != '\t') || c == 0x7f)) {
                    throw new IllegalArgumentException("Invalid HTTP header: " + header.getKey());
                }
            }
            if (!method.supportsBody() && requestBody.length > 0) {
                throw new IllegalArgumentException(
                        "HTTP method " + method + " does not conventionally support a request body");
            }

            return new TestConfig(this);
        }
    }
}
