package com.theuncoaffair.fishpipe.ui.imageviewer;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.theuncoaffair.fishpipe.data.DownloadEntry;

public final class ImageViewerUiState {
    @NonNull
    public final String title;
    public final boolean titleVisible;
    public final boolean story;
    public final boolean loading;
    public final boolean remote;
    @Nullable
    public final String remoteUrl;
    @Nullable
    public final Bitmap bitmap;
    @Nullable
    public final DownloadEntry entry;
    public final int finishNonce;

    public ImageViewerUiState(@NonNull String title,
                              boolean titleVisible,
                              boolean story,
                              boolean loading,
                              boolean remote,
                              @Nullable String remoteUrl,
                              @Nullable Bitmap bitmap,
                              @Nullable DownloadEntry entry,
                              int finishNonce) {
        this.title = title;
        this.titleVisible = titleVisible;
        this.story = story;
        this.loading = loading;
        this.remote = remote;
        this.remoteUrl = remoteUrl;
        this.bitmap = bitmap;
        this.entry = entry;
        this.finishNonce = finishNonce;
    }
}
