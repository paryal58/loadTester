package com.loadtester.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-friendly duration strings from the CLI (e.g. "30s", "5m",
 * "1500ms", "2h", "1h30m") into {@link Duration}.
 *
 * <p>We accept a sequence of one or more (number + unit) pairs, e.g.
 * "1h30m15s". This mirrors the ergonomics of tools like {@code k6} and
 * Go's {@code time.ParseDuration}, which is friendlier for CLI users than
 * forcing everything into raw seconds or ISO-8601 ("PT1H30M").
 *
 * <p>Supported units:
 * <ul>
 *   <li>{@code ms} - milliseconds</li>
 *   <li>{@code s}  - seconds</li>
 *   <li>{@code m}  - minutes</li>
 *   <li>{@code h}  - hours</li>
 * </ul>
 */
public final class DurationParser {

    // Matches one (number, unit) pair, e.g. "30", "s". Numbers may be
    // decimal (e.g. "1.5h") to allow fractional convenience units.
    private static final Pattern SEGMENT = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|s|m|h)");

    private DurationParser() {
        // Utility class - no instances.
    }

    /**
     * Parses a duration string such as {@code "30s"}, {@code "5m"}, or
     * {@code "1h30m"}.
     *
     * @param input the raw string from the command line
     * @return the parsed, non-negative {@link Duration}
     * @throws IllegalArgumentException if the string is empty, contains no
     *                                   valid segments, or contains trailing
     *                                   characters that were not consumed by
     *                                   any segment (which usually indicates
     *                                   a typo, e.g. a missing unit).
     */
    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration string must not be empty");
        }

        String trimmed = input.trim();
        Matcher matcher = SEGMENT.matcher(trimmed);

        long totalNanos = 0;
        int lastMatchEnd = 0;
        boolean matchedAtLeastOnce = false;

        while (matcher.find()) {
            // Guard against silently ignoring garbage between segments,
            // e.g. "30sx5m" - the "x" would otherwise just be skipped.
            if (matcher.start() != lastMatchEnd) {
                throw new IllegalArgumentException(
                        "Unrecognized characters in duration '" + input + "' near position " + lastMatchEnd);
            }

            double amount = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            totalNanos += toNanos(amount, unit);

            lastMatchEnd = matcher.end();
            matchedAtLeastOnce = true;
        }

        if (!matchedAtLeastOnce || lastMatchEnd != trimmed.length()) {
            throw new IllegalArgumentException(
                    "Could not parse duration '" + input + "'. Expected format like '30s', '5m', '1h30m'.");
        }

        return Duration.ofNanos(totalNanos);
    }

    private static long toNanos(double amount, String unit) {
        return switch (unit) {
            case "ms" -> (long) (amount * 1_000_000L);
            case "s" -> (long) (amount * 1_000_000_000L);
            case "m" -> (long) (amount * 60_000_000_000L);
            case "h" -> (long) (amount * 3_600_000_000_000L);
            default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
        };
    }
}
