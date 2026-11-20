package com.theuncoaffair.fishpipe.download;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.util.Notifications;
import com.theuncoaffair.fishpipe.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

@AndroidEntryPoint
public final class CaptionEmbedService extends Service {
    public static final String ACTION_START = "com.theuncoaffair.fishpipe.action.EMBED_CAPTIONS";
    public static final String ACTION_CANCEL = "com.theuncoaffair.fishpipe.action.CANCEL_EMBED";
    public static final String EXTRA_ROW_ID = "row_id";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private static volatile boolean running;
    private static volatile boolean cancelled;
    private static volatile long rowId;
    private static volatile int percent;

    @Inject Settings settings;

    private final Handler handler = new Handler(Looper.getMainLooper());

    public static boolean start(Context context, long id) {
        if (context == null || id <= 0L) return false;
        Intent intent = new Intent(context, CaptionEmbedService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ROW_ID, id);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Exception e) {
            Timber.tag("CaptionEmbedService").w(e, "Could not start caption embedding");
            return false;
        }
    }

    public static void cancel(Context context) {
        if (context == null || !running) return;
        Intent intent = new Intent(context, CaptionEmbedService.class).setAction(ACTION_CANCEL);
        try {
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void cancelFor(long id) {
        if (running && rowId != 0L && rowId == id) cancelled = true;
    }

    public static boolean isRunning() {
        return running;
    }

    public static long rowId() {
        return rowId;
    }

    public static int percent() {
        return percent;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            return START_NOT_STICKY;
        }
        if (running) {
            return START_NOT_STICKY;
        }
        long id = intent == null ? 0L : intent.getLongExtra(EXTRA_ROW_ID, 0L);
        DownloadDb db = new DownloadDb(this);
        DownloadEntry entry;
        try {
            entry = db.get(id);
        } finally {
            db.close();
        }
        if (entry == null || !Storage.exists(this, entry)) {
            Ui.toast(this, R.string.caption_embed_failed);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        running = true;
        cancelled = false;
        rowId = entry.rowId;
        percent = 0;
        Notifications.ensureChannel(this);
        startForegroundCompat(entry.title, 0);
        Ui.embedStarted(this);
        final DownloadEntry work = entry;
        WORKER.execute(() -> work(work));
        return START_NOT_STICKY;
    }

    private void startForegroundCompat(String title, int value) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(Notifications.CAPTION_ID,
                        Notifications.captionProgress(this, title, value),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(Notifications.CAPTION_ID,
                        Notifications.captionProgress(this, title, value));
            }
        } catch (Exception e) {
            Timber.tag("CaptionEmbedService").w(e, "startForeground failed");
        }
    }

    private void work(DownloadEntry entry) {
        final Context app = getApplicationContext();
        File input = null;
        File output = null;
        boolean inputTemp = false;
        boolean success = false;
        String error = null;
        try {
            Uri uri = Uri.parse(entry.localUri);
            if ("content".equals(uri.getScheme())) {
                input = File.createTempFile("caption_input_", ".mp4", app.getCacheDir());
                inputTemp = true;
                try (InputStream in = app.getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(input)) {
                    if (in == null) throw new IOException("missing input");
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            } else {
                String path = uri.getPath() == null ? entry.localUri : uri.getPath();
                input = new File(path);
                if (!input.isFile()) throw new IOException("missing input");
            }
            output = File.createTempFile("caption_output_", ".mp4", app.getCacheDir());
            boolean burned = CaptionBuilder.burnSidecar(app, entry, input, output,
                    new CaptionBuilder.Callback() {
                        @Override
                        public boolean isCancelled() {
                            return cancelled;
                        }

                        @Override
                        public void progress(long done, long total) {
                            int value = total > 0
                                    ? (int) Math.max(0, Math.min(100, done * 100L / total)) : 0;
                            if (value == percent) return;
                            percent = value;
                            handler.post(() -> {
                                if (running) startForegroundCompat(entry.title, value);
                            });
                        }
                    });
            if (cancelled) throw new InterruptedException("Cancelled");
            if (!burned) {
                error = app.getString(R.string.caption_embed_no_srt);
            } else {
                Storage.Saved saved = Storage.saveCaptionVideo(app, entry, output);
                DownloadEntry copy = new DownloadEntry();
                copy.mediaId = entry.mediaId;
                copy.title = entry.title;
                copy.author = entry.author;
                copy.authorAvatar = entry.authorAvatar;
                copy.caption = entry.caption;
                copy.hashtags = entry.hashtags;
                copy.pageUrl = entry.pageUrl;
                copy.type = DownloadEntry.TYPE_VIDEO;
                copy.thumbUrl = entry.thumbUrl;
                copy.story = entry.story;
                copy.captionMode = Settings.CAPTION_EMBED;
                copy.captionUrl = "";
                copy.captionLanguage = entry.captionLanguage;
                copy.captionAuto = entry.captionAuto;
                copy.localUri = saved.uri;
                copy.size = saved.size;
                copy.bytes = saved.size;
                copy.status = DownloadEntry.STATUS_COMPLETED;
                copy.createdAt = System.currentTimeMillis();
                copy.uploadedAt = entry.uploadedAt;
                copy.downloadedAt = copy.createdAt;
                DownloadDb db = new DownloadDb(app);
                try {
                    db.insert(copy);
                } finally {
                    db.close();
                }
                broadcastFinished(copy);
                success = true;
            }
        } catch (InterruptedException e) {
            error = app.getString(R.string.caption_embed_cancelled);
        } catch (Exception e) {
            Timber.tag("CaptionEmbedService").w(e, "Caption embedding failed");
            error = app.getString(R.string.caption_embed_failed);
        } finally {
            if (inputTemp && input != null) input.delete();
            if (output != null) output.delete();
        }
        final boolean done = success;
        final boolean wasCancelled = cancelled;
        final String message = error;
        handler.post(() -> finish(done, wasCancelled, message, entry));
    }

    private void finish(boolean success, boolean wasCancelled, String error, DownloadEntry entry) {
        running = false;
        cancelled = false;
        rowId = 0L;
        percent = 0;
        if (success) {
            Ui.embedFinished(this);
            if (Notifications.canPost(this) && settings.isNotifyEmbedFinished()) {
                Notifications.notifyCaptionDone(this,
                        Notifications.captionDone(this, entry.title));
            } else {
                Notifications.cancelCaption(this);
            }
        } else {
            Notifications.cancelCaption(this);
            Ui.toast(this, wasCancelled
                    ? getString(R.string.caption_embed_cancelled)
                    : error == null ? getString(R.string.caption_embed_failed) : error);
        }
        stopForeground(true);
        stopSelf();
    }

    private void broadcastFinished(DownloadEntry entry) {
        Intent intent = new Intent(DownloadService.ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(DownloadService.EXTRA_ID, entry.rowId);
        intent.putExtra(DownloadService.EXTRA_TITLE, entry.title);
        intent.putExtra(DownloadService.EXTRA_TYPE, entry.type);
        intent.putExtra(DownloadService.EXTRA_STATUS, entry.status);
        intent.putExtra(DownloadService.EXTRA_BYTES, entry.bytes);
        intent.putExtra(DownloadService.EXTRA_SIZE, entry.size);
        intent.putExtra(DownloadService.EXTRA_URI, entry.localUri);
        intent.putExtra(DownloadService.EXTRA_THUMB, entry.thumbUrl);
        sendBroadcast(intent);
    }
}
