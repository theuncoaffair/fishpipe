package com.theuncoaffair.fishpipe.download;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.theuncoaffair.fishpipe.FloatingDownloadActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.ui.DownloadGuard;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClipboardDownloader {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> PENDING = Collections.synchronizedSet(new HashSet<>());

    private ClipboardDownloader() {
    }

    public static void auto(Context context, String text) {
        download(context, text, false, 0L);
    }

    public static void auto(Context context, String text, long timestamp) {
        download(context, text, false, timestamp);
    }

    public static void manual(Context context, String text) {
        download(context, text, true, 0L);
    }

    private static void download(Context context, String text, boolean manual, long timestamp) {
        final Context app = context.getApplicationContext();
        String url = LinkUtils.findUrl(text);
        if (url.isEmpty()) {
            if (manual) Toast.makeText(app, R.string.clipboard_no_link, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LinkUtils.isSupported(url)) {
            if (manual) Toast.makeText(app, R.string.invalid_link, Toast.LENGTH_SHORT).show();
            return;
        }
        final Settings settings = AppGraph.get(app).settings();
        if (!manual && url.equals(settings.getLastClipboard())
                && (timestamp <= 0L || timestamp == settings.getLastClipboardTimestamp())) return;
        if (!PENDING.add(url)) return;
        settings.setLastClipboard(url);
        if (timestamp > 0L) settings.setLastClipboardTimestamp(timestamp);
        EXECUTOR.execute(() -> {
            MediaItem item = null;
            try {
                item = AppGraph.get(app).resolveRepository().resolve(url);
            } catch (Exception ignored) {
            }
            final MediaItem resolved = item;
            MAIN.post(() -> {
                PENDING.remove(url);
                if (resolved == null) {
                    if (manual) Toast.makeText(app, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (settings.isShowDownloadOptions() && openOptions(app, resolved, url)) return;
                List<DownloadEntry> existing = DownloadGuard.existingFor(app, resolved);
                if (!existing.isEmpty()) {
                    if (DownloadGuard.confirm(app, existing,
                            () -> enqueueDefault(app, resolved), null)) return;
                }
                int count = enqueueDefault(app, resolved);
                if (count > 0) {
                    Ui.downloadStarted(app);
                } else if (manual) {
                    Toast.makeText(app, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static int enqueueDefault(Context app, MediaItem item) {
        try {
            if (item.hasVideo()) return DownloadController.enqueueDefaultVideo(app, item);
            if (item.hasImages()) return DownloadController.enqueueDefaultPhotos(app, item);
            if (item.hasMusic()) return DownloadController.enqueueDefaultAudio(app, item);
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static boolean openOptions(Context context, MediaItem item, String link) {
        Intent intent = new Intent(context, FloatingDownloadActivity.class);
        intent.putExtra(FloatingDownloadActivity.EXTRA_ITEM, item);
        intent.putExtra(FloatingDownloadActivity.EXTRA_COPIED_LINK, link);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
