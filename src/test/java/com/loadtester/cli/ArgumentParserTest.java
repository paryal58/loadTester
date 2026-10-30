package com.loadtester.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentParserTest {

    @Test
    void rejectsNonFiniteRates() {
        assertThrows(CliParseException.class,
                () -> ArgumentParser.parse(new String[]{"--url", "http://localhost/", "--rate", "NaN"}));
        assertThrows(CliParseException.class,
                () -> ArgumentParser.parse(new String[]{"--url", "http://localhost/", "--rate", "Infinity"}));
    }

    @Test
    void rejectsConflictingRequestBodyOptions() {
        assertThrows(CliParseException.class, () -> ArgumentParser.parse(new String[]{
                "--url", "http://localhost/", "--body", "text", "--body-file", "body.json"
        }));
    }

    @Test
    void reportsMalformedRampUpAsCliError() {
        assertThrows(CliParseException.class,
                () -> ArgumentParser.parse(new String[]{"--url", "http://localhost/", "--ramp-up", "linear:-1:5"}));
    }

    @Test
    void reportsExcessiveClosedLoopConcurrencyAsCliError() {
        CliParseException exception = assertThrows(CliParseException.class, () ->
                ArgumentParser.parse(new String[]{
                        "--url", "http://localhost/",
                        "--concurrency", String.valueOf(com.loadtester.config.TestConfig.MAX_CLOSED_LOOP_CONCURRENCY + 1)
                }));

        assertTrue(exception.getMessage().contains("Closed-loop concurrency must be <="));
    }
}
