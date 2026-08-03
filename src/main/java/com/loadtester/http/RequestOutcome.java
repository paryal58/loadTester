package com.loadtester.http;

/**
 * The result of executing a single HTTP request, carrying everything the
 * metrics layer needs to record it correctly.
 *
 * <p>A key detail: {@code latencyNanos} is measured from
 * {@code intendedSendTimeNanos}, NOT from when the request was actually
 * dispatched. In closed-loop mode these two timestamps are identical (a
 * virtual user sends the moment it's ready to). In open-loop (rate) mode
 * they can diverge if the system under test - or this tool's own dispatch
 * path - falls behind schedule, and that divergence is exactly the signal
 * that reveals queueing/overload which naive "actual send to actual
 * receive" measurement would hide. See {@code OpenLoopRunner} for the full
 * explanation of why this matters (the "coordinated omission" problem).
 *
 * @param success              true if the request completed with a status code < 400
 *                             and no transport-level exception occurred
 * @param statusCode           HTTP status code, or -1 if the request never got a response
 *                             (connection refused, timeout, etc.)
 * @param intendedSendTimeNanos the scheduled dispatch time (nanoTime clock)
 * @param actualStartNanos      when the request was actually handed to the HTTP client
 * @param actualEndNanos        when the response (or failure) was received
 * @param latencyNanos          actualEndNanos - intendedSendTimeNanos (see class docs)
 * @param errorMessage          human-readable failure reason, or null on success
 */
public record RequestOutcome(
        boolean success,
        int statusCode,
        long intendedSendTimeNanos,
        long actualStartNanos,
        long actualEndNanos,
        long latencyNanos,
        String errorMessage
) {

    /** How long the request sat waiting to be dispatched before actually being sent. */
    public long dispatchDelayNanos() {
        return actualStartNanos - intendedSendTimeNanos;
    }

    /** How long the request itself took once actually sent (pure service time, no queueing). */
    public long serviceTimeNanos() {
        return actualEndNanos - actualStartNanos;
    }
}
