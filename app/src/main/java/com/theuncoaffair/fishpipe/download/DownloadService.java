package com.theuncoaffair.fishpipe.download;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.net.Http;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.util.FileMetadata;
import com.theuncoaffair.fishpipe.util.Notifications;
import com.theuncoaffair.fishpipe.util.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import timber.log.Timber;

@AndroidEntryPoint
public final class DownloadService extends Service {
    public static final String ACTION_PROCESS = "com.theuncoaffair.fishpipe.download.PROCESS";
    public static final String ACTION_PROGRESS = "com.theuncoaffair.fishpipe.download.PROGRESS";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_BYTES = "bytes";
    public static final String EXTRA_SIZE = "size";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_THUMB = "thumb";
    public static final String EXTRA_ERROR = "error";

    private static final Set<Long> CANCELLED = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private static final Set<Long> PAUSED = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private static final Map<Long, HttpURLConnection> CONNECTIONS = new ConcurrentHashMap<>();

    private final Object lock = new Object();
    private final Map<Long, DownloadEntry> active = new ConcurrentHashMap<>();

    @Inject Settings settings;

    private DownloadDb db;
    private ExecutorService pool;
    private Thread dispatcher;
    private volatile boolean destroyed;
    private long idleSince;

    public static void cancel(long rowId) {
        CANCELLED.add(rowId);
        PAUSED.remove(rowId);
        disconnect(rowId);
    }

    public static void pause(long rowId) {
        PAUSED.add(rowId);
        disconnect(rowId);
    }

    public static void clearPause(long rowId) {
        PAUSED.remove(rowId);
    }

    private static void disconnect(long rowId) {
        HttpURLConnection connection = CONNECTIONS.get(rowId);
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    public static File partFile(Context context, long rowId) {
        File dir = new File(context.getFilesDir(), "download_parts");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, "download_" + rowId + ".part");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = new DownloadDb(this);
        pool = Executors.newCachedThreadPool();
        Notifications.ensureChannel(this);
        // Downloads interrupted by a process death are safe to retry.
        db.requeueRunning();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(Notifications.PROGRESS_ID, serviceNotification());
        } catch (Exception e) {
            Timber.tag("DownloadService").w(e, "startForeground failed in onStartCommand");
        }
        synchronized (lock) {
            if (dispatcher == null || !dispatcher.isAlive()) {
                destroyed = false;
                dispatcher = new Thread(this::dispatchLoop, "download-dispatch");
                dispatcher.start();
            }
            lock.notifyAll();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void dispatchLoop() {
        while (!destroyed) {
            if (!NetworkPolicy.isAllowed(this, settings)) {
                if (active.isEmpty() && db.pendingCount() == 0) {
                    if (idleSince == 0) {
                        idleSince = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - idleSince > 1500) {
                        break;
                    }
                } else {
                    idleSince = 0;
                }
                if (active.isEmpty()) publishWaiting();
                synchronized (lock) {
                    if (!destroyed) {
                        try {
                            lock.wait(1200);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
                continue;
            }

            DownloadEntry entry = null;
            boolean stop = false;
            synchronized (lock) {
                while (!destroyed) {
                    int max = settings.getMaxTasks();
                    if (active.size() < max) {
                        entry = db.nextQueued();
                        if (entry != null) break;
                    }
                    if (active.isEmpty()) {
                        if (idleSince == 0) {
                            idleSince = System.currentTimeMillis();
                        } else if (System.currentTimeMillis() - idleSince > 1500) {
                            stop = true;
                            break;
                        }
                    } else {
                        idleSince = 0;
                    }
                    try {
                        lock.wait(500);
                    } catch (InterruptedException ignored) {
                    }
                }
                if (stop) break;
                if (entry != null) {
                    db.setStatus(entry.rowId, DownloadEntry.STATUS_RUNNING);
                    entry.status = DownloadEntry.STATUS_RUNNING;
                    boolean first = active.isEmpty();
                    active.put(entry.rowId, entry);
                    idleSince = 0;
                    if (first) Notifications.notifyStarted(this, Notifications.started(this, entry.title));
                }
            }

            if (entry == null) continue;
            publishForeground();
            final DownloadEntry work = entry;
            pool.execute(() -> {
                try {
                    process(work);
                } finally {
                    synchronized (lock) {
                        active.remove(work.rowId);
                        lock.notifyAll();
                    }
                    publishForeground();
                }
            });
        }
        stopForeground(true);
        stopSelf();
    }

    private void processSlideshow(DownloadEntry entry) {
        if (CANCELLED.contains(entry.rowId) || PAUSED.contains(entry.rowId)) {
            db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_CANCELLED);
            entry.status = DownloadEntry.STATUS_CANCELLED;
            broadcast(entry);
            return;
        }
        File temp = partFile(this, entry.rowId);
        entry.bytes = 0;
        broadcast(entry);
        try {
            if (!NetworkPolicy.isAllowed(this, settings)) throw new NetworkBlockedException();
            SlideshowBuilder.build(this, entry, temp, new SlideshowBuilder.Callback() {
                @Override
                public boolean isCancelled() {
                    return CANCELLED.contains(entry.rowId) || destroyed;
                }

                @Override
                public void progress(long done, long total) {
                    if (total <= 0) return;
                    long size = temp.length();
                    db.updateProgress(entry.rowId, done, total, DownloadEntry.STATUS_RUNNING);
                    entry.bytes = size;
                    publishForeground();
                }
            });
            if (CANCELLED.contains(entry.rowId)) throw new CancelledException();
            if (temp.length() == 0) throw new IOException("Empty slideshow");
            Storage.Saved saved = Storage.save(this, entry, temp, "video/mp4");
            if (CANCELLED.contains(entry.rowId)) {
                Storage.delete(this, saved.uri);
                throw new CancelledException();
            }
            db.complete(entry.rowId, saved.uri, saved.size, DownloadEntry.STATUS_COMPLETED);
            settings.completeFloatingDownload(entry.pageUrl);
            entry.status = DownloadEntry.STATUS_COMPLETED;
            entry.localUri = saved.uri;
            entry.size = saved.size;
            entry.bytes = saved.size;
            if (settings.isNotifyComplete()) {
                String label = Storage.displayName(this, saved.uri);
                if (label == null || label.trim().isEmpty()) label = entry.title;
                Notifications.notifyDone(this, Notifications.done(this, label));
            }
            Ui.downloadEnded(this, getString(R.string.notification_complete));
            broadcast(entry);
        } catch (NetworkBlockedException e) {
            db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_QUEUED);
            entry.status = DownloadEntry.STATUS_QUEUED;
            broadcast(entry);
        } catch (InterruptedException e) {
            temp.delete();
            if (PAUSED.contains(entry.rowId)) {
                db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_PAUSED);
                entry.status = DownloadEntry.STATUS_PAUSED;
            } else {
                db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_CANCELLED);
                entry.status = DownloadEntry.STATUS_CANCELLED;
            }
            broadcast(entry);
        } catch (Exception e) {
            temp.delete();
            Timber.tag("DownloadService").w(e, "Slideshow download failed type=%s", entry.type);
            if (CANCELLED.contains(entry.rowId)) {
                db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_CANCELLED);
                entry.status = DownloadEntry.STATUS_CANCELLED;
            } else if (PAUSED.contains(entry.rowId)) {
                db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_PAUSED);
                entry.status = DownloadEntry.STATUS_PAUSED;
            } else {
                db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_FAILED);
                entry.status = DownloadEntry.STATUS_FAILED;
            }
            broadcast(entry);
        }
    }

    private static boolean hasCaptions(DownloadEntry entry) {
        return entry.captionUrl != null && !entry.captionUrl.trim().isEmpty();
    }

    private void captionFailed(DownloadEntry entry, String stage, Exception e) {
        String message = stage + ": " + e.getClass().getSimpleName();
        String detail = e.getMessage();
        if (detail != null && !detail.trim().isEmpty()) message += " - " + detail.trim();
        if (entry != null && entry.title != null && !entry.title.trim().isEmpty()) {
            message += " (" + entry.title.trim() + ")";
        }
        Ui.toast(this, message);
        Notifications.notifyCaption(this, Notifications.captionFailed(this, message));
    }

    private void process(DownloadEntry entry) {
        if (entry.slideshow) {
            processSlideshow(entry);
            return;
        }
        if (CANCELLED.contains(entry.rowId)) {
            db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_CANCELLED);
            entry.status = DownloadEntry.STATUS_CANCELLED;
            entry.bytes = 0;
            broadcast(entry);
            return;
        }
        File temp = partFile(this, entry.rowId);
        long existing = temp.exists() ? temp.length() : 0;
        entry.bytes = existing;
        broadcast(entry);

        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        boolean keepPart = false;
        try {
            if (!NetworkPolicy.isAllowed(this, settings)) throw new NetworkBlockedException();
            boolean withHeaders = true;
            int code;
            while (true) {
                conn = openConnection(entry, existing, withHeaders);
                CONNECTIONS.put(entry.rowId, conn);
                code = conn.getResponseCode();
                if ((code == 401 || code == 403) && withHeaders) {
                    withHeaders = false;
                    CONNECTIONS.remove(entry.rowId);
                    conn.disconnect();
                    conn = null;
                    continue;
                }
                break;
            }
            if (code == 416) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                throw new IOException("Partial download is no longer valid");
            }
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);

            boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
            if (existing > 0 && code == HttpURLConnection.HTTP_OK) {
                // The server ignored Range; restart this file from byte zero.
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                existing = 0;
                entry.bytes = 0;
            }
            String contentType = conn.getContentType();
            long remoteSize = contentLength(conn);
            long total = append && remoteSize > 0 ? existing + remoteSize : remoteSize;
            entry.size = total;
            db.updateProgress(entry.rowId, existing, total, DownloadEntry.STATUS_RUNNING);
            publishForeground();

            in = conn.getInputStream();
            out = new FileOutputStream(temp, append);
            byte[] buffer = new byte[64 * 1024];
            long bytes = existing;
            long lastUpdate = 0;
            long lastNetworkCheck = SystemClock.elapsedRealtime();
            long transferStart = lastNetworkCheck;
            long limit = settings.getSpeedLimitKbps() * 1024L;
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (CANCELLED.contains(entry.rowId)) throw new CancelledException();
                if (PAUSED.contains(entry.rowId)) throw new PausedException();
                out.write(buffer, 0, n);
                bytes += n;
                entry.bytes = bytes;
                long now = SystemClock.elapsedRealtime();
                if (now - lastNetworkCheck >= 2000) {
                    lastNetworkCheck = now;
                    if (!NetworkPolicy.isAllowed(this, settings)) throw new NetworkBlockedException();
                }
                throttle(transferStart, bytes - existing, limit);
                if (now - lastUpdate >= 300) {
                    lastUpdate = now;
                    db.updateProgress(entry.rowId, bytes, total, DownloadEntry.STATUS_RUNNING);
                    broadcast(entry);
                    if (settings.isNotifyProgress()) publishForeground();
                }
            }
            out.flush();
            close(out);
            out = null;
            close(in);
            in = null;
            if (CANCELLED.contains(entry.rowId)) throw new CancelledException();
            if (PAUSED.contains(entry.rowId)) throw new PausedException();
            if (temp.length() == 0) throw new IOException("Empty response");

            CaptionBuilder.Callback captionCallback = new CaptionBuilder.Callback() {
                @Override
                public boolean isCancelled() {
                    return CANCELLED.contains(entry.rowId)
                            || PAUSED.contains(entry.rowId) || destroyed;
                }

                @Override
                public void progress(long done, long total) {
                }
            };
            File burned = new File(temp.getParentFile(), temp.getName() + ".captions.mp4");
            boolean embedded = false;
            if (hasCaptions(entry) && !Settings.CAPTION_SRT.equals(entry.captionMode)) {
                try {
                    embedded = CaptionBuilder.burnIn(entry, temp, burned, captionCallback);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    Timber.tag("DownloadService").w(e, "Caption burn-in failed");
                }
            }
            Storage.Saved saved = Storage.save(this, entry, temp, contentType);
            if (CANCELLED.contains(entry.rowId)) {
                Storage.delete(this, saved.uri);
                if (burned.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    burned.delete();
                }
                throw new CancelledException();
            }
            if (embedded) {
                try {
                    DownloadEntry copy = entry.copy();
                    copy.localUri = saved.uri;
                    copy.title = entry.title + " (" + getString(R.string.captions_section) + ")";
                    copy.captionMode = Settings.CAPTION_EMBED;
                    copy.captionUrl = "";
                    copy.captionLanguage = "";
                    copy.captionAuto = false;
                    copy.status = DownloadEntry.STATUS_COMPLETED;
                    Storage.Saved embeddedSaved = Storage.saveCaptionVideo(this, copy, burned);
                    copy.localUri = embeddedSaved.uri;
                    copy.size = embeddedSaved.size;
                    copy.bytes = embeddedSaved.size;
                    copy.downloadedAt = System.currentTimeMillis();
                    db.insert(copy);
                    db.complete(copy.rowId, embeddedSaved.uri, embeddedSaved.size,
                            DownloadEntry.STATUS_COMPLETED);
                    db.clearCaption(entry.rowId);
                    entry.captionMode = "";
                    entry.captionUrl = "";
                    entry.captionLanguage = "";
                    entry.captionAuto = false;
                } catch (Exception e) {
                    Timber.tag("DownloadService").w(e, "Caption copy save failed");
                    db.clearCaption(entry.rowId);
                    entry.captionMode = "";
                    entry.captionUrl = "";
                    entry.captionLanguage = "";
                    entry.captionAuto = false;
                }
            } else if (!Settings.CAPTION_SRT.equals(entry.captionMode)) {
                db.clearCaption(entry.rowId);
                entry.captionMode = "";
                entry.captionUrl = "";
                entry.captionLanguage = "";
                entry.captionAuto = false;
            }
            if (burned.exists()) {
                //noinspection ResultOfMethodCallIgnored
                burned.delete();
            }
            if (Settings.CAPTION_SRT.equals(entry.captionMode)) {
                if (!hasCaptions(entry)) {
                    captionFailed(entry, "select", new IOException("No caption track"));
                } else {
                    File srt = new File(temp.getParentFile(), temp.getName() + ".srt");
                    boolean written = false;
                    try {
                        CaptionBuilder.writeSrt(entry, srt, captionCallback);
                        written = true;
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Exception e) {
                        Timber.tag("DownloadService").w(e, "Caption write failed");
                        captionFailed(entry, "write", e);
                    }
                    if (written) {
                        try {
                            Storage.saveCaption(this, entry, srt, saved);
                            Storage.updateFileMetadata(this, saved.uri, entry,
                                    FileMetadata.saved(false, true));
                        } catch (Exception e) {
                            Timber.tag("DownloadService").w(e, "Caption save failed");
                            captionFailed(entry, "save", e);
                        }
                    }
                    //noinspection ResultOfMethodCallIgnored
                    srt.delete();
                }
            }
            if (CANCELLED.contains(entry.rowId)) {
                Storage.delete(this, saved.uri);
                throw new CancelledException();
            }
            db.complete(entry.rowId, saved.uri, saved.size, DownloadEntry.STATUS_COMPLETED);
            settings.completeFloatingDownload(entry.pageUrl);
            entry.status = DownloadEntry.STATUS_COMPLETED;
            entry.localUri = saved.uri;
            entry.size = saved.size;
            entry.bytes = saved.size;
            if (settings.isNotifyComplete()) {
                String label = Storage.displayName(this, saved.uri);
                if (label == null || label.trim().isEmpty()) label = entry.title;
                Notifications.notifyDone(this, Notifications.done(this, label));
            }
            Ui.downloadEnded(this, getString(R.string.notification_complete));
            broadcast(entry);
        } catch (NetworkBlockedException e) {
            keepPart = temp.exists();
            if (CANCELLED.contains(entry.rowId)) {
                db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_CANCELLED);
                entry.status = DownloadEntry.STATUS_CANCELLED;
                entry.bytes = 0;
            } else if (PAUSED.contains(entry.rowId)) {
                db.updateProgress(entry.rowId, entry.bytes, 0, DownloadEntry.STATUS_PAUSED);
                entry.status = DownloadEntry.STATUS_PAUSED;
            } else {
                db.updateProgress(entry.rowId, entry.bytes, 0, DownloadEntry.STATUS_QUEUED);
                entry.status = DownloadEntry.STATUS_QUEUED;
            }
            broadcast(entry);
        } catch (CancelledException e) {
            db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_CANCELLED);
            entry.status = DownloadEntry.STATUS_CANCELLED;
            entry.bytes = 0;
            broadcast(entry);
        } catch (Exception e) {
            if (PAUSED.contains(entry.rowId)) {
                keepPart = temp.exists();
                db.updateProgress(entry.rowId, entry.bytes, 0, DownloadEntry.STATUS_PAUSED);
                entry.status = DownloadEntry.STATUS_PAUSED;
                broadcast(entry);
            } else if (CANCELLED.contains(entry.rowId)) {
                db.updateProgress(entry.rowId, 0, 0, DownloadEntry.STATUS_CANCELLED);
                entry.status = DownloadEntry.STATUS_CANCELLED;
                entry.bytes = 0;
                broadcast(entry);
            } else {
                keepPart = temp.exists();
                Timber.tag("DownloadService").w(e, "Download failed type=%s", entry.type);
                db.updateProgress(entry.rowId, entry.bytes, 0, DownloadEntry.STATUS_FAILED);
                entry.status = DownloadEntry.STATUS_FAILED;
                if (settings.isNotifyComplete()) {
                    Notifications.notifyDone(this, Notifications.failed(this, entry.title));
                }
                Ui.downloadEnded(this, getString(R.string.notification_failed));
                broadcast(entry);
            }
        } finally {
            CONNECTIONS.remove(entry.rowId);
            close(in);
            close(out);
            if (conn != null) conn.disconnect();
            if (temp.exists() && !keepPart) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
            CANCELLED.remove(entry.rowId);
            PAUSED.remove(entry.rowId);
        }
    }

    private void throttle(long start, long bytes, long limit) {
        if (limit <= 0 || bytes <= 0) return;
        long expected = bytes * 1000L / limit;
        long elapsed = SystemClock.elapsedRealtime() - start;
        long wait = expected - elapsed;
        while (wait > 0) {
            try {
                Thread.sleep(Math.min(wait, 200));
            } catch (InterruptedException ignored) {
                return;
            }
            wait -= 200;
        }
    }

    private void publishForeground() {
        if (destroyed) return;
        try {
            startForeground(Notifications.PROGRESS_ID, serviceNotification());
        } catch (Exception e) {
            Timber.tag("DownloadService").w(e, "startForeground failed in publishForeground");
        }
    }

    private void publishWaiting() {
        if (destroyed) return;
        if (db.pendingCount() <= 0) return;
        try {
            startForeground(Notifications.PROGRESS_ID,
                    Notifications.foreground(this, getString(R.string.waiting_network)));
        } catch (Exception e) {
            Timber.tag("DownloadService").w(e, "startForeground failed in publishWaiting");
        }
    }

    private Notification serviceNotification() {
        if (!settings.isNotifyProgress()) {
            return Notifications.foreground(this, getString(R.string.downloading));
        }
        DownloadEntry latest = null;
        for (DownloadEntry entry : active.values()) {
            if (latest == null || entry.bytes > latest.bytes) latest = entry;
        }
        if (latest == null) {
            return Notifications.progress(this, getString(R.string.downloading),
                    getString(R.string.preparing_download), 0, true);
        }
        int percent = latest.progressPercent();
        String text;
        if (active.size() > 1) {
            text = getString(R.string.notification_progress_multi, active.size(), percent);
        } else if (latest.size > 0) {
            text = percent + "%  •  " + Ui.formatBytes(latest.bytes) + " / " + Ui.formatBytes(latest.size);
        } else {
            text = getString(R.string.downloading) + "  •  " + Ui.formatBytes(latest.bytes);
        }
        String title = MediaItem.notEmpty(latest.mediaId) ? latest.mediaId : latest.title;
        return Notifications.progress(this, title, text, percent, latest.size <= 0);
    }

    private HttpURLConnection openConnection(DownloadEntry entry, long existing,
                                             boolean withHeaders) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(entry.remoteUrl).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", Http.MOBILE_UA);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (withHeaders) {
            conn.setRequestProperty("Referer", referer(entry));
            conn.setRequestProperty("Cookie", "tt_webid_v2=" + LinkUtils.randomWid());
        }
        if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
        return conn;
    }

    private String referer(DownloadEntry entry) {
        String url = entry.remoteUrl == null ? "" : entry.remoteUrl;
        if (url.contains("douyin") || url.contains("iesdouyin")) return "https://www.douyin.com/";
        return "https://www.tiktok.com/";
    }

    private static long contentLength(HttpURLConnection conn) {
        String value = conn.getHeaderField("Content-Length");
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return conn.getContentLength();
    }

    private void broadcast(DownloadEntry entry) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_ID, entry.rowId);
        intent.putExtra(EXTRA_TITLE, entry.title);
        intent.putExtra(EXTRA_TYPE, entry.type);
        intent.putExtra(EXTRA_STATUS, entry.status);
        intent.putExtra(EXTRA_BYTES, entry.bytes);
        intent.putExtra(EXTRA_SIZE, entry.size);
        intent.putExtra(EXTRA_URI, entry.localUri);
        intent.putExtra(EXTRA_THUMB, entry.thumbUrl);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        synchronized (lock) {
            lock.notifyAll();
        }
        for (HttpURLConnection connection : CONNECTIONS.values()) {
            try {
                connection.disconnect();
            } catch (Exception ignored) {
            }
        }
        CONNECTIONS.clear();
        if (pool != null) pool.shutdownNow();
        super.onDestroy();
    }

    private static void close(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }

    private static void close(OutputStream out) {
        if (out == null) return;
        try {
            out.close();
        } catch (Exception ignored) {
        }
    }

    private static final class CancelledException extends IOException {
    }

    private static final class NetworkBlockedException extends IOException {
    }

    private static final class PausedException extends IOException {
    }
}
