package com.theuncoaffair.fishpipe.ui.insights;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.theuncoaffair.fishpipe.analytics.InsightsResult;

public final class InsightsUiState {
    private final boolean loading;
    @Nullable
    private final InsightsResult result;
    @Nullable
    private final String error;

    private InsightsUiState(boolean loading, @Nullable InsightsResult result,
                            @Nullable String error) {
        this.loading = loading;
        this.result = result;
        this.error = error;
    }

    @NonNull
    public static InsightsUiState idle() {
        return new InsightsUiState(false, null, null);
    }

    @NonNull
    public static InsightsUiState loading() {
        return new InsightsUiState(true, null, null);
    }

    @NonNull
    public static InsightsUiState success(@NonNull InsightsResult result) {
        return new InsightsUiState(false, result, null);
    }

    @NonNull
    public static InsightsUiState error(@Nullable String message) {
        return new InsightsUiState(false, null, message);
    }

    public boolean isLoading() {
        return loading;
    }

    @Nullable
    public InsightsResult getResult() {
        return result;
    }

    @Nullable
    public String getError() {
        return error;
    }
}
