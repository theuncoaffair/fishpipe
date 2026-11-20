package com.theuncoaffair.fishpipe.ui.insights;

import java.util.Locale;

public final class InsightFormat {
    private InsightFormat() {
    }

    public static String compact(long value) {
        if (value >= 1_000_000_000_000L) {
            return String.format(Locale.US, "%.1fT", value / 1_000_000_000_000.0);
        }
        if (value >= 1_000_000_000L) {
            return String.format(Locale.US, "%.1fB", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) return String.format(Locale.US, "%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}
