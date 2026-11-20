package com.theuncoaffair.fishpipe.ui.wallpaper;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.di.AppModule;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class WallpaperViewModel extends ViewModel {
    private final Context context;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<WallpaperUiState> state = new MutableLiveData<>();

    private String uri;
    private boolean video;
    private long durationMs;
    private Bitmap current;
    private int pendingLoads;
    private int framePercent;
    private boolean busy;
    private boolean seekEnabled;
    private int toastRes;
    private int toastNonce;
    private int finishNonce;

    @Inject
    public WallpaperViewModel(@ApplicationContext Context context,
                              @Named(AppModule.IO) ExecutorService io) {
        this.context = context;
        this.io = io;
        publish();
    }

    public LiveData<WallpaperUiState> getState() {
        return state;
    }

    public void initialize(@Nullable String rawUri, boolean isVideo) {
        if (uri != null) return;
        if (rawUri == null || rawUri.trim().isEmpty()) {
            toast(R.string.file_not_found);
            finishNonce++;
            publish();
            return;
        }
        uri = rawUri;
        video = isVideo;
        busy = true;
        publish();
        if (video) prepareVideo();
        else loadImage();
    }

    public void loadFrame(int percent) {
        if (!video || durationMs <= 0) return;
        final int value = Math.max(0, Math.min(100, percent));
        framePercent = value;
        pendingLoads++;
        busy = true;
        publish();
        final long atMs = durationMs * value / 100L;
        io.execute(() -> {
            Bitmap frame = videoFrame(atMs);
            main.post(() -> {
                pendingLoads--;
                if (frame != null) {
                    current = frame;
                    seekEnabled = true;
                }
                busy = pendingLoads > 0;
                publish();
            });
        });
    }

    public void apply() {
        if (current == null || busy) {
            toast(R.string.wallpaper_failed);
            publish();
            return;
        }
        busy = true;
        publish();
        io.execute(() -> {
            boolean ok;
            try {
                WallpaperManager manager = WallpaperManager.getInstance(context);
                manager.setBitmap(current);
                ok = true;
            } catch (Exception ignored) {
                ok = false;
            }
            final boolean success = ok;
            main.post(() -> {
                busy = false;
                if (success) {
                    toast(R.string.wallpaper_set);
                    finishNonce++;
                } else {
                    toast(R.string.wallpaper_failed);
                }
                publish();
            });
        });
    }

    private void prepareVideo() {
        io.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            long duration = 0;
            Bitmap frame = null;
            try {
                retriever.setDataSource(context, Uri.parse(uri));
                String value = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (value != null) duration = Long.parseLong(value);
                frame = retriever.getFrameAtTime(0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            } catch (Exception ignored) {
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
            final long finalDuration = duration;
            final Bitmap finalFrame = frame;
            main.post(() -> {
                durationMs = finalDuration;
                if (finalFrame != null) {
                    current = finalFrame;
                    seekEnabled = finalDuration > 0;
                }
                busy = false;
                if (finalFrame == null) toast(R.string.wallpaper_failed);
                publish();
            });
        });
    }

    private void loadImage() {
        io.execute(() -> {
            Bitmap bitmap = decode();
            main.post(() -> {
                busy = false;
                if (bitmap == null) {
                    toast(R.string.wallpaper_failed);
                } else {
                    current = bitmap;
                }
                publish();
            });
        });
    }

    @Nullable
    private Bitmap videoFrame(long atMs) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.parse(uri));
            return retriever.getFrameAtTime(atMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    @Nullable
    private Bitmap decode() {
        try {
            Uri parsed = Uri.parse(uri);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream first = open(parsed)) {
                BitmapFactory.decodeStream(first, null, bounds);
            }
            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > 2560) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream second = open(parsed)) {
                return BitmapFactory.decodeStream(second, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private InputStream open(Uri uri) throws Exception {
        if ("content".equals(uri.getScheme())) {
            return context.getContentResolver().openInputStream(uri);
        }
        return new FileInputStream(uri.getPath());
    }

    private void toast(int res) {
        toastRes = res;
        toastNonce++;
    }

    private void publish() {
        state.setValue(new WallpaperUiState(
                video,
                busy,
                current != null && !busy,
                seekEnabled && !busy,
                framePercent,
                current,
                toastRes,
                toastNonce,
                finishNonce));
    }
}
