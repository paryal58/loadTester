package com.loadtester.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {

    @Test
    void parsesSeconds() {
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s"));
    }

    @Test
    void parsesMinutes() {
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5m"));
    }

    @Test
    void parsesHours() {
        assertEquals(Duration.ofHours(2), DurationParser.parse("2h"));
    }

    @Test
    void parsesMilliseconds() {
        assertEquals(Duration.ofMillis(1500), DurationParser.parse("1500ms"));
    }

    @Test
    void parsesCompoundDurations() {
        assertEquals(Duration.ofMinutes(90), DurationParser.parse("1h30m"));
        assertEquals(Duration.ofSeconds(90), DurationParser.parse("1m30s"));
    }

    @Test
    void parsesFractionalUnits() {
        assertEquals(Duration.ofMillis(1500), DurationParser.parse("1.5s"));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
    }

    @Test
    void rejectsMissingUnit() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("30"));
    }

    @Test
    void rejectsGarbageCharacters() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("30sx5m"));
    }

    @Test
    void rejectsUnknownUnit() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("30d"));
    }
}
