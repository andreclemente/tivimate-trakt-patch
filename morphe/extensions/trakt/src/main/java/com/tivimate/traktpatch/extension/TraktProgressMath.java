package com.tivimate.traktpatch.extension;

/** Converts persisted millisecond positions to Trakt's 0..100 progress range. */
public final class TraktProgressMath {
    private TraktProgressMath() { }

    public static double percent(long positionMs, long durationMs) {
        if (durationMs <= 0L) throw new IllegalArgumentException("duration must be positive");
        double value = (positionMs * 100.0d) / durationMs;
        return Math.max(0.0d, Math.min(100.0d, value));
    }
}
