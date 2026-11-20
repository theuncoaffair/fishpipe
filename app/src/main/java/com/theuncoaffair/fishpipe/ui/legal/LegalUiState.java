package com.theuncoaffair.fishpipe.ui.legal;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public final class LegalUiState {
    @StringRes public final int titleRes;
    @NonNull public final CharSequence body;
    public final boolean loaded;
    public final boolean error;

    public LegalUiState(@StringRes int titleRes, @NonNull CharSequence body,
                        boolean loaded, boolean error) {
        this.titleRes = titleRes;
        this.body = body;
        this.loaded = loaded;
        this.error = error;
    }
}
