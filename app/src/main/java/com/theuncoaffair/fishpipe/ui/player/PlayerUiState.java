package com.theuncoaffair.fishpipe.ui.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.theuncoaffair.fishpipe.data.DownloadEntry;

public final class PlayerUiState {
    @NonNull
    public final String title;
    @NonNull
    public final String shareUrl;
    @NonNull
    public final String author;
    @NonNull
    public final String caption;
    @NonNull
    public final String hashtags;
    public final boolean story;
    public final long uploadedAt;
    public final int repeatMode;
    public final float speed;
    @NonNull
    public final String speedLabel;
    public final boolean muted;
    public final boolean captionsVisible;
    @StringRes
    public final int toastRes;
    public final int toastNonce;
    @Nullable
    public final DownloadEntry openEntry;
    public final int openNonce;

    public PlayerUiState(@NonNull String title,
                         @NonNull String shareUrl,
                         @NonNull String author,
                         @NonNull String caption,
                         @NonNull String hashtags,
                         boolean story,
                         long uploadedAt,
                         int repeatMode,
                         float speed,
                         @NonNull String speedLabel,
                         boolean muted,
                         boolean captionsVisible,
                         @StringRes int toastRes,
                         int toastNonce,
                         @Nullable DownloadEntry openEntry,
                         int openNonce) {
        this.title = title;
        this.shareUrl = shareUrl;
        this.author = author;
        this.caption = caption;
        this.hashtags = hashtags;
        this.story = story;
        this.uploadedAt = uploadedAt;
        this.repeatMode = repeatMode;
        this.speed = speed;
        this.speedLabel = speedLabel;
        this.muted = muted;
        this.captionsVisible = captionsVisible;
        this.toastRes = toastRes;
        this.toastNonce = toastNonce;
        this.openEntry = openEntry;
        this.openNonce = openNonce;
    }

    @NonNull
    public static PlayerUiState idle() {
        return new PlayerUiState("", "", "", "", "", false, 0L,
                PlayerViewModel.REPEAT_OFF, 1f, "1x", false, true,
                0, 0, null, 0);
    }
}
