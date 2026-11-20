package com.theuncoaffair.fishpipe.region;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class RegionUiState {
    @NonNull public final List<Region> regions;
    @NonNull public final String query;
    @NonNull public final String selectedCode;
    public final boolean loading;
    public final boolean error;

    public RegionUiState(@NonNull List<Region> regions, @NonNull String query,
                         @NonNull String selectedCode, boolean loading, boolean error) {
        this.regions = Collections.unmodifiableList(regions);
        this.query = query;
        this.selectedCode = selectedCode;
        this.loading = loading;
        this.error = error;
    }

    public boolean isEmpty() {
        return !loading && !error && regions.isEmpty();
    }
}
