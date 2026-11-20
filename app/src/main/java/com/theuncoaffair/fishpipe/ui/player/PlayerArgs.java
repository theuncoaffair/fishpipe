package com.theuncoaffair.fishpipe.ui.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.theuncoaffair.fishpipe.data.MediaItem;

public final class PlayerArgs {
    @NonNull
    public final String url;
    @NonNull
    public final String title;
    @NonNull
    public final String fileName;
    @Nullable
    public final MediaItem mediaItem;
    public final boolean batch;
    public final int repeatMode;

    public PlayerArgs(@Nullable String url,
                      @Nullable String title,
                      @Nullable String fileName,
                      @Nullable MediaItem mediaItem,
                      boolean batch,
                      int repeatMode) {
        this.url = clean(url);
        this.title = clean(title);
        this.fileName = clean(fileName);
        this.mediaItem = mediaItem;
        this.batch = batch;
        this.repeatMode = repeatMode;
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value;
    }
}
