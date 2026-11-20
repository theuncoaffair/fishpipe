package com.theuncoaffair.fishpipe.ui.language;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class LanguageUiState {
    @NonNull public final List<LanguageOption> options;
    @NonNull public final String query;
    @NonNull public final String selectedCode;

    public LanguageUiState(@NonNull List<LanguageOption> options, @NonNull String query,
                           @NonNull String selectedCode) {
        this.options = Collections.unmodifiableList(options);
        this.query = query;
        this.selectedCode = selectedCode;
    }

    public boolean isEmpty() {
        return options.isEmpty();
    }
}
