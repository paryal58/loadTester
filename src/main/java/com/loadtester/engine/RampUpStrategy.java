package com.loadtester.engine;

/**
 * Defines ramp-up curves for the load test.
 */
public interface RampUpStrategy {

    /**
     * @param elapsedMillis milliseconds since the test started
     * @return the target load value at this instant
     */
    double getTarget(long elapsedMillis);

    /**
     * Parses a ramp-up spec from the CLI into a strategy instance.
     */
    static RampUpStrategy parse(String spec, double peakValue) {
        if (spec == null || spec.isBlank() || spec.equalsIgnoreCase("constant")) {
            return new Constant(peakValue);
        }

        String[] parts = spec.split(":");
        String type = parts[0].toLowerCase();

        try {
            return switch (type) {
                case "constant" -> new Constant(peakValue);
                case "linear" -> {
                    requireParamCount(parts, 3, "linear:<startValue>:<rampSeconds>");
                    double start = Double.parseDouble(parts[1]);
                    double rampSeconds = Double.parseDouble(parts[2]);
                    yield new Linear(start, peakValue, rampSeconds);
                }
                case "step" -> {
                    requireParamCount(parts, 3, "step:<numSteps>:<stepSeconds>");
                    int numSteps = Integer.parseInt(parts[1]);
                    double stepSeconds = Double.parseDouble(parts[2]);
                    yield new Step(numSteps, stepSeconds, peakValue);
                }
                case "spike" -> {
                    requireParamCount(parts, 4, "spike:<baselineValue>:<atSecond>:<spikeDurationSeconds>");
                    double baseline = Double.parseDouble(parts[1]);
                    double atSecond = Double.parseDouble(parts[2]);
                    double spikeDurationSeconds = Double.parseDouble(parts[3]);
                    yield new Spike(baseline, peakValue, atSecond, spikeDurationSeconds);
                }
                default -> throw new IllegalArgumentException(
                        "Unknown ramp-up type '" + type + "'. Expected one of: constant, linear, step, spike");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric parameter in ramp-up spec '" + spec + "'");
        }
    }

    private static void requireParamCount(String[] parts, int expected, String usageExample) {
        if (parts.length != expected) {
            throw new IllegalArgumentException(
                    "Ramp-up spec '" + String.join(":", parts) + "' has the wrong number of parameters. "
                            + "Expected format: " + usageExample);
        }
    }

    // Implementations.

    /** Holds the peak value for the whole test. */
    record Constant(double peakValue) implements RampUpStrategy {
        public Constant {
            requireFiniteNonNegative(peakValue, "peak value");
        }

        @Override
        public double getTarget(long elapsedMillis) {
            return peakValue;
        }
    }

    /**
     * Ramps linearly from the start value to the peak value.
     */
    record Linear(double startValue, double peakValue, double rampSeconds) implements RampUpStrategy {
        public Linear {
            requireFiniteNonNegative(startValue, "start value");
            requireFiniteNonNegative(peakValue, "peak value");
            if (!Double.isFinite(rampSeconds) || rampSeconds <= 0) {
                throw new IllegalArgumentException("Ramp duration must be a finite positive number");
            }
        }

        @Override
        public double getTarget(long elapsedMillis) {
            double elapsedSeconds = elapsedMillis / 1000.0;
            if (elapsedSeconds >= rampSeconds) {
                return peakValue;
            }
            double progress = elapsedSeconds / rampSeconds; // 0.0 .. 1.0
            return startValue + progress * (peakValue - startValue);
        }
    }

    /**
     * Steps through a fixed number of load levels.
     */
    record Step(int numSteps, double stepSeconds, double peakValue) implements RampUpStrategy {
        public Step {
            if (numSteps < 1) {
                throw new IllegalArgumentException("Number of steps must be >= 1");
            }
            if (!Double.isFinite(stepSeconds) || stepSeconds <= 0) {
                throw new IllegalArgumentException("Step duration must be a finite positive number");
            }
            requireFiniteNonNegative(peakValue, "peak value");
        }

        @Override
        public double getTarget(long elapsedMillis) {
            double elapsedSeconds = elapsedMillis / 1000.0;
            int currentStep = (int) (elapsedSeconds / stepSeconds) + 1; // steps are 1-indexed
            currentStep = Math.min(currentStep, numSteps);
            return peakValue * ((double) currentStep / numSteps);
        }
    }

    /**
     * Applies a temporary spike above the baseline value.
     */
    record Spike(double baselineValue, double peakValue, double atSecond, double spikeDurationSeconds)
            implements RampUpStrategy {
        public Spike {
            requireFiniteNonNegative(baselineValue, "baseline value");
            requireFiniteNonNegative(peakValue, "peak value");
            requireFiniteNonNegative(atSecond, "spike start");
            if (!Double.isFinite(spikeDurationSeconds) || spikeDurationSeconds <= 0) {
                throw new IllegalArgumentException("Spike duration must be a finite positive number");
            }
        }

        @Override
        public double getTarget(long elapsedMillis) {
            double elapsedSeconds = elapsedMillis / 1000.0;
            boolean inSpikeWindow = elapsedSeconds >= atSecond
                    && elapsedSeconds < atSecond + spikeDurationSeconds;
            return inSpikeWindow ? peakValue : baselineValue;
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a finite non-negative number");
        }
    }
}
