package com.theuncoaffair.fishpipe.ui.audioplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.theuncoaffair.fishpipe.data.DownloadEntry;

public final class AudioPlayerUiState {
    @NonNull
    public final String fileName;
    @NonNull
    public final String title;
    @NonNull
    public final String author;
    @NonNull
    public final String coverUrl;
    @NonNull
    public final String pageUrl;
    @NonNull
    public final String audioUrl;
    public final boolean story;
    public final long uploadedAt;
    public final boolean prepared;
    public final boolean playing;
    public final boolean loading;
    public final int positionMs;
    public final int durationMs;
    public final float speed;
    @NonNull
    public final String speedLabel;
    public final int repeatMode;
    @NonNull
    public final String caption;
    @StringRes
    public final int toastRes;
    public final int toastNonce;
    public final int finishNonce;
    @Nullable
    public final DownloadEntry openEntry;
    public final int openNonce;

    public AudioPlayerUiState(@NonNull String fileName,
                              @NonNull String title,
                              @NonNull String author,
                              @NonNull String coverUrl,
                              @NonNull String pageUrl,
                              @NonNull String audioUrl,
                              boolean story,
                              long uploadedAt,
                              boolean prepared,
                              boolean playing,
                              boolean loading,
                              int positionMs,
                              int durationMs,
                              float speed,
                              @NonNull String speedLabel,
                              int repeatMode,
                              @NonNull String caption,
                              @StringRes int toastRes,
                              int toastNonce,
                              int finishNonce,
                              @Nullable DownloadEntry openEntry,
                              int openNonce) {
        this.fileName = fileName;
        this.title = title;
        this.author = author;
        this.coverUrl = coverUrl;
        this.pageUrl = pageUrl;
        this.audioUrl = audioUrl;
        this.story = story;
        this.uploadedAt = uploadedAt;
        this.prepared = prepared;
        this.playing = playing;
        this.loading = loading;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.speed = speed;
        this.speedLabel = speedLabel;
        this.repeatMode = repeatMode;
        this.caption = caption;
        this.toastRes = toastRes;
        this.toastNonce = toastNonce;
        this.finishNonce = finishNonce;
        this.openEntry = openEntry;
        this.openNonce = openNonce;
    }

    @NonNull
    public static AudioPlayerUiState idle() {
        return new AudioPlayerUiState("", "", "", "", "", "", false, 0L,
                false, false, false, 0, 0, 1f, "1x",
                AudioPlayerViewModel.REPEAT_OFF, "", 0, 0, 0, null, 0);
    }
}
