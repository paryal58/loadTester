package com.loadtester.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
