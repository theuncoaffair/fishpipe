package com.theuncoaffair.fishpipe.ui.main;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class MainUiState {
    @NonNull public final String language;
    @NonNull public final String theme;
    public final boolean stayInApp;
    public final boolean autoDetect;
    public final boolean autoDownload;
    public final boolean pro;
    public final int remaining;
    public final int running;

    public MainUiState(@NonNull String language, @NonNull String theme, boolean stayInApp,
                       boolean autoDetect, boolean autoDownload, boolean pro, int remaining,
                       int running) {
        this.language = language;
        this.theme = theme;
        this.stayInApp = stayInApp;
        this.autoDetect = autoDetect;
        this.autoDownload = autoDownload;
        this.pro = pro;
        this.remaining = remaining;
        this.running = running;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MainUiState)) return false;
        MainUiState that = (MainUiState) other;
        return stayInApp == that.stayInApp
                && autoDetect == that.autoDetect
                && autoDownload == that.autoDownload
                && pro == that.pro
                && remaining == that.remaining
                && running == that.running
                && language.equals(that.language)
                && theme.equals(that.theme);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language, theme, stayInApp, autoDetect, autoDownload, pro, remaining,
                running);
    }
}
