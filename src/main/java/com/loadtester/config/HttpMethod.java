package com.loadtester.config;

/**
 * The HTTP methods this tool can send. Kept as a closed enum (rather than
 * a raw String) so invalid methods are rejected at CLI-parse time instead
 * of failing deep inside {@code java.net.http.HttpClient} with a less
 * helpful error.
 */
public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD;

    /**
     * True if this method conventionally carries a request body.
     * Used to decide whether to attach the configured body/content-type.
     */
    public boolean supportsBody() {
        return this == POST || this == PUT || this == PATCH || this == DELETE;
    }

    /** Case-insensitive lookup with a friendly error message on failure. */
    public static HttpMethod fromString(String value) {
        try {
            return HttpMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported HTTP method '" + value + "'. Supported: GET, POST, PUT, DELETE, PATCH, HEAD.");
        }
    }
}
