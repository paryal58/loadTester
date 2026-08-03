package com.loadtester.cli;

import com.loadtester.config.HttpMethod;
import com.loadtester.config.TestConfig;
import com.loadtester.util.DurationParser;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses CLI flags into a TestConfig.
 */
public final class ArgumentParser {

    private ArgumentParser() {
        // Utility class.
    }

    public static TestConfig parse(String[] args) {
        if (args.length == 0) {
            throw new CliParseException("No arguments provided. Use --help to see usage.");
        }

        TestConfig.Builder builder = TestConfig.builder();
        String rawBody = null;
        String bodyFile = null;
        String contentType = null;

        List<String> remaining = new ArrayList<>(List.of(args));
        int i = 0;
        while (i < remaining.size()) {
            String flag = remaining.get(i);

            if (flag.equals("--help") || flag.equals("-h")) {
                printUsage();
                System.exit(0);
            }

            String value = requireValue(remaining, i, flag);
            // Flags with values consume two tokens.
            switch (flag) {
                case "--url" -> builder.targetUrl(parseUri(value));
                case "--method" -> builder.method(HttpMethod.fromString(value));
                case "--concurrency", "-c" -> builder.concurrency(parsePositiveInt(value, "--concurrency"));
                case "--rate", "-r" -> builder.targetRate(parsePositiveDouble(value, "--rate"));
                case "--duration", "-d" -> builder.testDuration(parseDurationFlag(value, "--duration"));
                case "--warmup" -> builder.warmupDuration(parseDurationFlag(value, "--warmup"));
                case "--ramp-up" -> builder.rampUpSpec(value);
                case "--header" -> applyHeader(builder, value);
                case "--body" -> { rawBody = value; }
                case "--body-file" -> { bodyFile = value; }
                case "--content-type" -> { contentType = value; }
                case "--timeout" -> builder.requestTimeout(parseDurationFlag(value, "--timeout"));
                case "--think-time" -> builder.thinkTime(parseDurationFlag(value, "--think-time"));
                case "--output" -> builder.outputFile(Path.of(value));
                default -> throw new CliParseException("Unknown flag: " + flag + " (use --help for usage)");
            }
            i += 2;
        }

        if (contentType != null) {
            builder.addHeader("Content-Type", contentType);
        }

        byte[] bodyBytes = resolveBody(rawBody, bodyFile);
        if (bodyBytes.length > 0) {
            builder.requestBody(bodyBytes);
        }

        try {
            return builder.build();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CliParseException(e.getMessage());
        }
    }

    // ---- individual flag handlers -----------------------------------

    private static String requireValue(List<String> args, int index, String flag) {
        if (index + 1 >= args.size()) {
            throw new CliParseException("Flag " + flag + " requires a value");
        }
        return args.get(index + 1);
    }

    private static URI parseUri(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new CliParseException("--url must be an absolute URL, e.g. http://localhost:8080/path, got: " + value);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new CliParseException("Invalid --url value '" + value + "': " + e.getMessage());
        }
    }

    private static int parsePositiveInt(String value, String flagName) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1) {
                throw new CliParseException(flagName + " must be a positive integer, got " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new CliParseException(flagName + " must be a positive integer, got '" + value + "'");
        }
    }

    private static double parsePositiveDouble(String value, String flagName) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed <= 0) {
                throw new CliParseException(flagName + " must be a positive number, got " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new CliParseException(flagName + " must be a positive number, got '" + value + "'");
        }
    }

    private static Duration parseDurationFlag(String value, String flagName) {
        try {
            return DurationParser.parse(value);
        } catch (IllegalArgumentException e) {
            throw new CliParseException(flagName + ": " + e.getMessage());
        }
    }

    private static void applyHeader(TestConfig.Builder builder, String value) {
        int colonIndex = value.indexOf(':');
        if (colonIndex < 0) {
            throw new CliParseException("--header must be in 'Key: Value' format, got '" + value + "'");
        }
        String key = value.substring(0, colonIndex).trim();
        String val = value.substring(colonIndex + 1).trim();
        if (key.isEmpty()) {
            throw new CliParseException("--header key must not be empty, got '" + value + "'");
        }
        builder.addHeader(key, val);
    }

    private static byte[] resolveBody(String rawBody, String bodyFile) {
        if (bodyFile != null) {
            try {
                return Files.readAllBytes(Path.of(bodyFile));
            } catch (IOException e) {
                throw new CliParseException("Could not read --body-file '" + bodyFile + "': " + e.getMessage());
            }
        }
        if (rawBody != null) {
            return rawBody.getBytes(StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    // ---- usage text ----------------------------------------------------

    public static void printUsage() {
        System.out.println("""
                Mini Load Tester - a from-scratch HTTP load testing CLI

                USAGE:
                  loadtester --url <URL> [OPTIONS]

                REQUIRED:
                  --url URL                    Target endpoint, e.g. http://localhost:8080/api

                LOAD SHAPE:
                  --concurrency, -c N           Virtual users (closed-loop) or max in-flight
                                                 requests (open-loop). Default: 10
                  --rate, -r N                  Target requests/sec. Presence of this flag
                                                 switches the tool into OPEN-LOOP mode, which
                                                 corrects for coordinated omission (see README).
                                                 Absence means CLOSED-LOOP (pure concurrency) mode.
                  --ramp-up SPEC                 Ramp-up pattern. Default: constant
                                                    constant
                                                    linear:<startValue>:<rampSeconds>
                                                    step:<numSteps>:<stepSeconds>
                                                    spike:<baselineValue>:<atSecond>:<spikeDurationSeconds>
                                                 The "peak" value ramps toward is --rate (open-loop)
                                                 or --concurrency (closed-loop).

                TIMING:
                  --duration, -d DURATION       Test duration, e.g. 30s, 5m, 1h. Default: 30s
                  --warmup DURATION              Warmup period excluded from results. Default: 0s
                  --timeout DURATION              Per-request timeout. Default: 10s
                  --think-time DURATION           Delay between requests per VU, closed-loop only. Default: 0s

                REQUEST SHAPE:
                  --method METHOD                GET|POST|PUT|DELETE|PATCH|HEAD. Default: GET
                  --header "Key: Value"           Repeatable.
                  --body TEXT                     Raw request body string.
                  --body-file PATH                 Read request body from a file (overrides --body).
                  --content-type TYPE               Shorthand for --header "Content-Type: TYPE".

                OUTPUT:
                  --output PATH                    Write a report to PATH. Format inferred from
                                                    extension: .json or .csv.

                  --help, -h                       Show this message.

                EXAMPLES:
                  # Closed-loop: 50 virtual users hammering an endpoint for 1 minute
                  loadtester --url http://localhost:8080/ -c 50 -d 1m

                  # Open-loop: sustain exactly 200 req/s for 2 minutes (coordinated-omission safe)
                  loadtester --url http://localhost:8080/ -r 200 -c 500 -d 2m

                  # Ramp from 0 to 300 req/s over 30s, then hold for the rest of a 3-minute test
                  loadtester --url http://localhost:8080/ -r 300 -c 1000 -d 3m --ramp-up linear:0:30

                  # POST JSON with a body file and export results
                  loadtester --url http://localhost:8080/orders --method POST \\
                      --body-file order.json --content-type application/json \\
                      -c 20 -d 30s --output results.json
                """);
    }
}
