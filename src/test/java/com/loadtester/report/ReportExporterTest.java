package com.loadtester.report;

import com.loadtester.metrics.TestResults;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExporterTest {

    @Test
    void exportCreatesParentDirectoriesWhenNeeded() throws Exception {
        Path outputFile = Files.createTempDirectory("mini-load-tester-").resolve("nested/reports/result.json");

        TestResults results = new TestResults(
                10L,
                9L,
                1L,
                Duration.ofSeconds(1),
                10.0,
                10_000_000L,
                20_000_000L,
                30_000_000L,
                40_000_000L,
                50_000_000L,
                60_000_000L,
                70_000_000L,
                80_000_000L,
                20_000_000.0,
                Map.of(200, 9L),
                Map.of("bad\nerror", 1L),
                0L,
                0L,
                0L
        );

        ReportExporter.export(results, outputFile);

        assertTrue(Files.exists(outputFile));
        String report = Files.readString(outputFile);
        assertTrue(report.contains("\"totalRequests\""));
        assertTrue(report.contains("\"bad\\nerror\""));
        assertTrue(report.contains("\"droppedRequests\": 0"));
        assertTrue(report.contains("\"missedArrivals\": 0"));
    }

    @Test
    void rejectsUnsupportedOutputExtensions() {
        TestResults results = new TestResults(
                0, 0, 0, Duration.ofSeconds(1), 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of(), 0, 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> ReportExporter.export(results, Path.of("report.txt")));
    }

    @Test
    void csvIncludesCompactSortedStatusCodeBreakdown() throws Exception {
        Path outputFile = Files.createTempDirectory("mini-load-tester-").resolve("result.csv");
        TestResults results = new TestResults(
                10, 8, 2, Duration.ofSeconds(1), 10,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(500, 1L, 200, 8L, -1, 1L), Map.of(), 0, 0, 0);

        ReportExporter.export(results, outputFile);

        String[] lines = Files.readString(outputFile).split("\\R");
        String[] headers = lines[0].split(",", -1);
        String[] values = lines[1].split(",", -1);
        int statusCodesColumn = java.util.Arrays.asList(headers).indexOf("statusCodes");
        int missedArrivalsColumn = java.util.Arrays.asList(headers).indexOf("missedArrivals");

        assertTrue(statusCodesColumn >= 0);
        assertTrue(missedArrivalsColumn >= 0);
        assertTrue(values.length > statusCodesColumn);
        assertEquals("0", values[missedArrivalsColumn]);
        assertEquals("no_response=1;200=8;500=1", values[statusCodesColumn]);
    }
}
