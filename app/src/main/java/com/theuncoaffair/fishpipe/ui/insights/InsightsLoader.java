package com.theuncoaffair.fishpipe.ui.insights;

import androidx.annotation.NonNull;

import com.theuncoaffair.fishpipe.analytics.InsightsResult;

public interface InsightsLoader {
    @NonNull
    InsightsResult load(@NonNull String query, int mode) throws Exception;
}
