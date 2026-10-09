package com.loadtester.report;

import com.loadtester.metrics.TestResults;
import com.loadtester.util.FormatUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Writes test results to JSON or CSV based on the output file extension.
 */
public final class ReportExporter {

    private ReportExporter() {
    }

    /** Writes results to the given output file. */
    public static void export(TestResults results, Path outputFile) {
        if (outputFile.getFileName() == null) {
            throw new IllegalArgumentException("Output path must include a file name");
        }
        String fileName = outputFile.getFileName().toString().toLowerCase();
        String content = switch (fileName.substring(fileName.lastIndexOf('.') + 1)) {
            case "csv" -> toCsv(results);
            case "json" -> toJson(results);
            default -> throw new IllegalArgumentException("Output file must end in .json or .csv: " + outputFile);
        };

        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report to " + outputFile, e);
        }
    }

    /** Renders results as a flat JSON object. */
    private static String toJson(TestResults r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, "totalRequests", r.totalRequests(), true);
        appendJsonField(sb, "successCount", r.successCount(), true);
        appendJsonField(sb, "errorCount", r.errorCount(), true);
        appendJsonField(sb, "errorRatePercent", round2(r.errorRate() * 100), true);
        appendJsonField(sb, "measuredDurationSeconds", round2(r.actualDuration().toNanos() / 1_000_000_000.0), true);
        appendJsonField(sb, "throughputPerSecond", round2(r.throughputPerSecond()), true);
        appendJsonField(sb, "droppedRequests", r.droppedRequests(), true);

        sb.append("  \"latencyMs\": {\n");
        appendJsonField(sb, "    min", round2(FormatUtils.nanosToMillis(r.minLatencyNanos())), true);
        appendJsonField(sb, "    p50", round2(FormatUtils.nanosToMillis(r.p50LatencyNanos())), true);
        appendJsonField(sb, "    p75", round2(FormatUtils.nanosToMillis(r.p75LatencyNanos())), true);
        appendJsonField(sb, "    p90", round2(FormatUtils.nanosToMillis(r.p90LatencyNanos())), true);
        appendJsonField(sb, "    p95", round2(FormatUtils.nanosToMillis(r.p95LatencyNanos())), true);
        appendJsonField(sb, "    p99", round2(FormatUtils.nanosToMillis(r.p99LatencyNanos())), true);
        appendJsonField(sb, "    p999", round2(FormatUtils.nanosToMillis(r.p999LatencyNanos())), true);
        appendJsonField(sb, "    max", round2(FormatUtils.nanosToMillis(r.maxLatencyNanos())), true);
        appendJsonField(sb, "    mean", round2(FormatUtils.nanosToMillis((long) r.meanLatencyNanos())), false);
        sb.append("  },\n");

        sb.append("  \"statusCodes\": {\n");
        appendIntMapEntries(sb, r.statusCodeCounts());
        sb.append("  },\n");

        sb.append("  \"errors\": {\n");
        appendStringMapEntries(sb, r.errorTypeCounts());
        sb.append("  },\n");

        appendJsonField(sb, "dispatchDelaySamples", r.dispatchDelaySamples(), false);
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendJsonField(StringBuilder sb, String key, Object value, boolean trailingComma) {
        sb.append("  \"").append(key.trim()).append("\": ").append(value);
        sb.append(trailingComma ? ",\n" : "\n");
    }

    private static void appendIntMapEntries(StringBuilder sb, Map<Integer, Long> map) {
        Map<Integer, Long> sorted = new java.util.TreeMap<>(map);
        int i = 0;
        for (Map.Entry<Integer, Long> entry : sorted.entrySet()) {
            String key = entry.getKey() == -1 ? "no_response" : String.valueOf(entry.getKey());
            sb.append("    \"").append(key).append("\": ").append(entry.getValue());
            sb.append(++i < sorted.size() ? ",\n" : "\n");
        }
    }

    private static void appendStringMapEntries(StringBuilder sb, Map<String, Long> map) {
        Map<String, Long> sorted = new java.util.TreeMap<>(map);
        int i = 0;
        for (Map.Entry<String, Long> entry : sorted.entrySet()) {
            sb.append("    \"").append(escapeJson(entry.getKey())).append("\": ").append(entry.getValue());
            sb.append(++i < sorted.size() ? ",\n" : "\n");
        }
    }

    private static String escapeJson(String s) {
        StringBuilder escaped = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Renders results as a single-row CSV file. */
    private static String toCsv(TestResults r) {
        String header = String.join(",",
                "totalRequests", "successCount", "errorCount", "errorRatePercent",
                "measuredDurationSeconds", "throughputPerSecond",
                "minMs", "p50Ms", "p75Ms", "p90Ms", "p95Ms", "p99Ms", "p999Ms", "maxMs", "meanMs",
                "dispatchDelaySamples", "droppedRequests");

        String row = String.join(",",
                String.valueOf(r.totalRequests()),
                String.valueOf(r.successCount()),
                String.valueOf(r.errorCount()),
                String.valueOf(round2(r.errorRate() * 100)),
                String.valueOf(round2(r.actualDuration().toNanos() / 1_000_000_000.0)),
                String.valueOf(round2(r.throughputPerSecond())),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.minLatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.p50LatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.p75LatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.p90LatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.p95LatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.p99LatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.p999LatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis(r.maxLatencyNanos()))),
                String.valueOf(round2(FormatUtils.nanosToMillis((long) r.meanLatencyNanos()))),
                String.valueOf(r.dispatchDelaySamples()),
                String.valueOf(r.droppedRequests())
        );

        return header + "\n" + row + "\n";
    }
}
