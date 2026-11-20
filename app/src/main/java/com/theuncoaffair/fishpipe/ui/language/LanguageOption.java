package com.theuncoaffair.fishpipe.ui.language;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class LanguageOption {
    @NonNull public final String code;
    @NonNull public final String label;
    public final boolean selected;

    public LanguageOption(@NonNull String code, @NonNull String label, boolean selected) {
        this.code = code;
        this.label = label;
        this.selected = selected;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LanguageOption)) return false;
        LanguageOption that = (LanguageOption) other;
        return selected == that.selected && code.equals(that.code) && label.equals(that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, label, selected);
    }
}
