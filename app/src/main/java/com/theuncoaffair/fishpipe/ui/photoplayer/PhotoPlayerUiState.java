package com.theuncoaffair.fishpipe.ui.photoplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.theuncoaffair.fishpipe.data.MediaItem;

public final class PhotoPlayerUiState {
    @Nullable
    public final MediaItem item;
    @NonNull
    public final String title;
    @NonNull
    public final String currentUrl;
    public final boolean story;
    public final int index;
    public final int total;
    @StringRes
    public final int toastRes;
    public final int toastNonce;
    public final int finishNonce;

    public PhotoPlayerUiState(@Nullable MediaItem item,
                              @NonNull String title,
                              @NonNull String currentUrl,
                              boolean story,
                              int index,
                              int total,
                              @StringRes int toastRes,
                              int toastNonce,
                              int finishNonce) {
        this.item = item;
        this.title = title;
        this.currentUrl = currentUrl;
        this.story = story;
        this.index = index;
        this.total = total;
        this.toastRes = toastRes;
        this.toastNonce = toastNonce;
        this.finishNonce = finishNonce;
    }
}
