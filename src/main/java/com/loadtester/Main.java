package com.loadtester;

import com.loadtester.cli.ArgumentParser;
import com.loadtester.cli.CliParseException;
import com.loadtester.config.TestConfig;
import com.loadtester.engine.LoadTestEngine;

/**
 * CLI entry point. Responsibilities are deliberately narrow: parse
 * arguments, construct the engine, run it, and translate failures into
 * clean exit codes/messages rather than raw stack traces. All actual test
 * logic lives in {@link LoadTestEngine} and the packages it delegates to.
 */
public final class Main {

    public static void main(String[] args) {
        TestConfig config;
        try {
            config = ArgumentParser.parse(args);
        } catch (CliParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Use --help for usage information.");
            System.exit(1);
            return;
        }

        try {
            new LoadTestEngine(config).run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Test interrupted.");
            System.exit(130); // conventional exit code for SIGINT
        } catch (Exception e) {
            System.err.println("Load test failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
