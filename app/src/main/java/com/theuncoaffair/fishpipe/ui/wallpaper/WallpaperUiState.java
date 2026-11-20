package com.theuncoaffair.fishpipe.ui.wallpaper;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public final class WallpaperUiState {
    public final boolean video;
    public final boolean busy;
    public final boolean setEnabled;
    public final boolean seekEnabled;
    public final int framePercent;
    @Nullable
    public final Bitmap preview;
    @StringRes
    public final int toastRes;
    public final int toastNonce;
    public final int finishNonce;

    public WallpaperUiState(boolean video,
                            boolean busy,
                            boolean setEnabled,
                            boolean seekEnabled,
                            int framePercent,
                            @Nullable Bitmap preview,
                            @StringRes int toastRes,
                            int toastNonce,
                            int finishNonce) {
        this.video = video;
        this.busy = busy;
        this.setEnabled = setEnabled;
        this.seekEnabled = seekEnabled;
        this.framePercent = framePercent;
        this.preview = preview;
        this.toastRes = toastRes;
        this.toastNonce = toastNonce;
        this.finishNonce = finishNonce;
    }

    @NonNull
    public static WallpaperUiState initial(boolean video) {
        return new WallpaperUiState(video, true, false, false, 0, null, 0, 0, 0);
    }
}
