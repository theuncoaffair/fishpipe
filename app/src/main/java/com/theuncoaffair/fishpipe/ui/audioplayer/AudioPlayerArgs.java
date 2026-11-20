package com.theuncoaffair.fishpipe.ui.audioplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AudioPlayerArgs {
    @NonNull
    public final String url;
    @NonNull
    public final String title;
    @NonNull
    public final String author;
    @NonNull
    public final String cover;
    @NonNull
    public final String pageUrl;
    @NonNull
    public final String fileName;
    public final int repeatMode;

    public AudioPlayerArgs(@Nullable String url,
                           @Nullable String title,
                           @Nullable String author,
                           @Nullable String cover,
                           @Nullable String pageUrl,
                           @Nullable String fileName,
                           int repeatMode) {
        this.url = clean(url);
        this.title = clean(title);
        this.author = clean(author);
        this.cover = clean(cover);
        this.pageUrl = clean(pageUrl);
        this.fileName = clean(fileName);
        this.repeatMode = repeatMode;
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value;
    }
}
