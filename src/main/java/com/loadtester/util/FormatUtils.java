package com.loadtester.util;

import java.util.Locale;

/**
 * Small collection of formatting helpers used by the console reporter and
 * exporters. Kept separate from reporting logic so the formatting rules
 * (rounding, units, padding) are easy to find and change in one place.
 */
public final class FormatUtils {

    private FormatUtils() {
        // Utility class - no instances.
    }

    /** Converts nanoseconds to milliseconds, formatted with 2 decimal places. */
    public static String nanosToMillisString(long nanos) {
        double millis = nanos / 1_000_000.0;
        return String.format(Locale.US, "%.2f", millis);
    }

    /** Converts nanoseconds to milliseconds as a raw double (no formatting). */
    public static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    /** Formats a fraction (0.0-1.0) as a percentage string, e.g. 0.0234 -> "2.34%". */
    public static String percentage(double fraction) {
        return String.format(Locale.US, "%.2f%%", fraction * 100.0);
    }

    /** Formats a double to a fixed number of decimal places. */
    public static String decimal(double value, int places) {
        return String.format(Locale.US, "%." + places + "f", value);
    }

    /**
     * Adds thousands separators to an integer count, e.g. 1234567 -> "1,234,567".
     * Pure JDK, avoids pulling in a formatting dependency for something this small.
     */
    public static String withThousandsSeparator(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** Right-pads a string with spaces to the given width (for table alignment). */
    public static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
    }

    /** Left-pads a string with spaces to the given width (for table alignment). */
    public static String padLeft(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        return " ".repeat(width - s.length()) + s;
    }

    /** Repeats a character n times - used for drawing table borders/separators. */
    public static String repeat(char c, int times) {
        return String.valueOf(c).repeat(Math.max(0, times));
    }
}
