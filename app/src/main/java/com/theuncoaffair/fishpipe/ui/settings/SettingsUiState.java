package com.theuncoaffair.fishpipe.ui.settings;

import androidx.annotation.NonNull;

public final class SettingsUiState {
    @NonNull public final String language;
    @NonNull public final String theme;
    @NonNull public final String regionCode;
    @NonNull public final String regionName;
    @NonNull public final String folderMode;
    @NonNull public final String customResolver;
    @NonNull public final String versionName;
    public final int maxTasks;
    public final int remaining;
    public final boolean proActive;
    public final boolean notifyProgress;
    public final boolean notifyComplete;

    public SettingsUiState(@NonNull String language, @NonNull String theme,
                           @NonNull String regionCode, @NonNull String regionName,
                           @NonNull String folderMode, @NonNull String customResolver,
                           @NonNull String versionName, int maxTasks, int remaining,
                           boolean proActive, boolean notifyProgress, boolean notifyComplete) {
        this.language = language;
        this.theme = theme;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.folderMode = folderMode;
        this.customResolver = customResolver;
        this.versionName = versionName;
        this.maxTasks = maxTasks;
        this.remaining = remaining;
        this.proActive = proActive;
        this.notifyProgress = notifyProgress;
        this.notifyComplete = notifyComplete;
    }

    public boolean notificationsEnabled() {
        return notifyProgress || notifyComplete;
    }
}
