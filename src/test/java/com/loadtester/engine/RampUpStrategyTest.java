package com.loadtester.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RampUpStrategyTest {

    private static final double DELTA = 0.001;

    @Test
    void constantAlwaysReturnsPeakValue() {
        RampUpStrategy strategy = RampUpStrategy.parse("constant", 100.0);
        assertEquals(100.0, strategy.getTarget(0), DELTA);
        assertEquals(100.0, strategy.getTarget(60_000), DELTA);
    }

    @Test
    void blankSpecDefaultsToConstant() {
        RampUpStrategy strategy = RampUpStrategy.parse("", 50.0);
        assertEquals(50.0, strategy.getTarget(5_000), DELTA);
    }

    @Test
    void linearRampsCorrectlyAndHoldsAfterRampWindow() {
        // Ramp from 0 to 100 over 10 seconds.
        RampUpStrategy strategy = RampUpStrategy.parse("linear:0:10", 100.0);
        assertEquals(0.0, strategy.getTarget(0), DELTA);
        assertEquals(50.0, strategy.getTarget(5_000), DELTA);
        assertEquals(100.0, strategy.getTarget(10_000), DELTA);
        assertEquals(100.0, strategy.getTarget(20_000), DELTA); // holds after ramp window
    }

    @Test
    void linearRampFromNonZeroStart() {
        RampUpStrategy strategy = RampUpStrategy.parse("linear:20:10", 100.0);
        assertEquals(20.0, strategy.getTarget(0), DELTA);
        assertEquals(60.0, strategy.getTarget(5_000), DELTA);
    }

    @Test
    void stepIncreasesInDiscreteIncrements() {
        // 5 steps of 10s each, peak 100 -> steps at 20, 40, 60, 80, 100
        RampUpStrategy strategy = RampUpStrategy.parse("step:5:10", 100.0);
        assertEquals(20.0, strategy.getTarget(0), DELTA);
        assertEquals(20.0, strategy.getTarget(9_000), DELTA);
        assertEquals(40.0, strategy.getTarget(10_000), DELTA);
        assertEquals(100.0, strategy.getTarget(45_000), DELTA);
        assertEquals(100.0, strategy.getTarget(100_000), DELTA); // clamped at max steps
    }

    @Test
    void spikeHoldsBaselineExceptDuringSpikeWindow() {
        // baseline=10, spike to peak at t=20s for 5s
        RampUpStrategy strategy = RampUpStrategy.parse("spike:10:20:5", 100.0);
        assertEquals(10.0, strategy.getTarget(0), DELTA);
        assertEquals(10.0, strategy.getTarget(19_000), DELTA);
        assertEquals(100.0, strategy.getTarget(20_000), DELTA);
        assertEquals(100.0, strategy.getTarget(24_000), DELTA);
        assertEquals(10.0, strategy.getTarget(25_000), DELTA);
        assertEquals(10.0, strategy.getTarget(60_000), DELTA);
    }

    @Test
    void rejectsUnknownRampType() {
        assertThrows(IllegalArgumentException.class, () -> RampUpStrategy.parse("exponential:1:2", 100.0));
    }

    @Test
    void rejectsWrongParameterCount() {
        assertThrows(IllegalArgumentException.class, () -> RampUpStrategy.parse("linear:10", 100.0));
    }

    @Test
    void rejectsNonNumericParameters() {
        assertThrows(IllegalArgumentException.class, () -> RampUpStrategy.parse("linear:abc:10", 100.0));
    }
}
