package com.theuncoaffair.fishpipe.ui.downloadsettings;

import androidx.annotation.NonNull;

public final class DownloadSettingsUiState {
    @NonNull public final String downloadPath;
    @NonNull public final String folderMode;
    @NonNull public final String defaultVideo;
    @NonNull public final String defaultPhotos;
    @NonNull public final String networkPolicy;
    public final int maxTasks;
    public final int speedLimitKbps;
    public final boolean showDownloadOptions;
    public final boolean toastDownloadStart;
    public final boolean toastDownloadEnd;
    public final boolean toastEmbedStart;
    public final boolean toastEmbedFinished;
    public final boolean captionsEnabled;

    public DownloadSettingsUiState(@NonNull String downloadPath, @NonNull String folderMode,
                                   @NonNull String defaultVideo, @NonNull String defaultPhotos,
                                   @NonNull String networkPolicy, int maxTasks, int speedLimitKbps,
                                   boolean showDownloadOptions, boolean toastDownloadStart,
                                   boolean toastDownloadEnd, boolean toastEmbedStart,
                                   boolean toastEmbedFinished, boolean captionsEnabled) {
        this.downloadPath = downloadPath;
        this.folderMode = folderMode;
        this.defaultVideo = defaultVideo;
        this.defaultPhotos = defaultPhotos;
        this.networkPolicy = networkPolicy;
        this.maxTasks = maxTasks;
        this.speedLimitKbps = speedLimitKbps;
        this.showDownloadOptions = showDownloadOptions;
        this.toastDownloadStart = toastDownloadStart;
        this.toastDownloadEnd = toastDownloadEnd;
        this.toastEmbedStart = toastEmbedStart;
        this.toastEmbedFinished = toastEmbedFinished;
        this.captionsEnabled = captionsEnabled;
    }
}
