package com.loadtester.cli;

/**
 * Thrown when command-line arguments are malformed or fail validation.
 * Caught in {@code Main} to print a clean, one-line error plus usage
 * instructions instead of a raw stack trace.
 */
public class CliParseException extends RuntimeException {
    public CliParseException(String message) {
        super(message);
    }

    public CliParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
