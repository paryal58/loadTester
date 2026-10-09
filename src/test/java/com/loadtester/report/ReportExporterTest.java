package com.loadtester.report;

import com.loadtester.metrics.TestResults;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

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
                0L
        );

        ReportExporter.export(results, outputFile);

        assertTrue(Files.exists(outputFile));
        String report = Files.readString(outputFile);
        assertTrue(report.contains("\"totalRequests\""));
        assertTrue(report.contains("\"bad\\nerror\""));
        assertTrue(report.contains("\"droppedRequests\": 0"));
    }

    @Test
    void rejectsUnsupportedOutputExtensions() {
        TestResults results = new TestResults(
                0, 0, 0, Duration.ofSeconds(1), 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of(), 0, 0);

        assertThrows(IllegalArgumentException.class,
                () -> ReportExporter.export(results, Path.of("report.txt")));
    }
}
