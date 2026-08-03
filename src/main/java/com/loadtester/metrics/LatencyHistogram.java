package com.loadtester.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Stores latency samples in a fixed-size histogram for approximate percentiles.
 */
public final class LatencyHistogram {

    /** Smallest latency we bother distinguishing: 1 microsecond. */
    private static final long MIN_TRACKABLE_NANOS = 1_000L;

    /** Largest latency we track before lumping into an overflow bucket: 5 minutes. */
    private static final long MAX_TRACKABLE_NANOS = 5L * 60 * 1_000_000_000L;

    /** Each bucket is 2% wider than the last -> ~1% max relative error on any percentile. */
    private static final double GROWTH_FACTOR = 1.02;

    private static final double LOG_GROWTH_FACTOR = Math.log(GROWTH_FACTOR);

    private static final int BUCKET_COUNT =
            (int) Math.ceil(Math.log((double) MAX_TRACKABLE_NANOS / MIN_TRACKABLE_NANOS) / LOG_GROWTH_FACTOR) + 2;

    private final AtomicLongArray bucketCounts = new AtomicLongArray(BUCKET_COUNT);
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNanos = new AtomicLong(Long.MIN_VALUE);
    // Sum is kept for computing the mean.
    private final AtomicLong sumNanos = new AtomicLong(0);

    /** Records one latency sample. */
    public void record(long latencyNanos) {
        if (latencyNanos < 0) {
            // Clamp negative values so they do not corrupt the histogram.
            latencyNanos = 0;
        }

        int bucketIndex = bucketIndexFor(latencyNanos);
        bucketCounts.incrementAndGet(bucketIndex);
        totalCount.incrementAndGet();
        sumNanos.addAndGet(latencyNanos);

        updateMin(latencyNanos);
        updateMax(latencyNanos);
    }

    private int bucketIndexFor(long latencyNanos) {
        if (latencyNanos <= MIN_TRACKABLE_NANOS) {
            return 0;
        }
        if (latencyNanos >= MAX_TRACKABLE_NANOS) {
            return BUCKET_COUNT - 1; // overflow bucket
        }
        int index = (int) (Math.log((double) latencyNanos / MIN_TRACKABLE_NANOS) / LOG_GROWTH_FACTOR);
        return Math.min(index, BUCKET_COUNT - 1);
    }

    /** Returns the lower bound of the given bucket in nanoseconds. */
    private long bucketLowerBoundNanos(int bucketIndex) {
        if (bucketIndex == 0) {
            return 0;
        }
        return (long) (MIN_TRACKABLE_NANOS * Math.pow(GROWTH_FACTOR, bucketIndex));
    }

    private void updateMin(long value) {
        minNanos.updateAndGet(current -> Math.min(current, value));
    }

    private void updateMax(long value) {
        maxNanos.updateAndGet(current -> Math.max(current, value));
    }

    /**
     * Returns the approximate value (in nanoseconds) at the given percentile.
     *
     * @param percentile a value in (0, 100], e.g. 50.0, 95.0, 99.9
     */
    public long percentile(double percentile) {
        long total = totalCount.get();
        if (total == 0) {
            return 0;
        }

        // Use ceil so percentiles follow the usual definition.
        long targetRank = (long) Math.ceil((percentile / 100.0) * total);
        targetRank = Math.max(1, Math.min(targetRank, total));

        long cumulative = 0;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            cumulative += bucketCounts.get(i);
            if (cumulative >= targetRank) {
                return bucketLowerBoundNanos(i);
            }
        }
        return maxNanos.get();
    }

    public long count() {
        return totalCount.get();
    }

    public long min() {
        long v = minNanos.get();
        return v == Long.MAX_VALUE ? 0 : v;
    }

    public long max() {
        long v = maxNanos.get();
        return v == Long.MIN_VALUE ? 0 : v;
    }

    public double mean() {
        long total = totalCount.get();
        return total == 0 ? 0.0 : (double) sumNanos.get() / total;
    }
}
