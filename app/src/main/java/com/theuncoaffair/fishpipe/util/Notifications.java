package com.theuncoaffair.fishpipe.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import com.theuncoaffair.fishpipe.MainActivity;
import com.theuncoaffair.fishpipe.R;

public final class Notifications {
    public static final String CHANNEL = "downloads";
    public static final String CHANNEL_DONE = "downloads_done_v2";
    public static final String CHANNEL_START = "downloads_start";
    public static final String CHANNEL_CLIPBOARD = "clipboard_links";
    public static final String CHANNEL_WATCH = "clipboard_watch";
    public static final int START_ID = 4200;
    public static final int PROGRESS_ID = 4201;
    public static final int DONE_ID = 4202;
    public static final int CLIPBOARD_ID = 4203;
    public static final int CLIPBOARD_WATCH_ID = 4204;
    public static final int CAPTION_ID = 4205;
    public static final int CAPTION_DONE_ID = 4206;

    private Notifications() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel progress = new NotificationChannel(CHANNEL,
                context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
        progress.setShowBadge(false);
        nm.createNotificationChannel(progress);
        NotificationChannel done = new NotificationChannel(CHANNEL_DONE,
                context.getString(R.string.notification_channel_done), NotificationManager.IMPORTANCE_DEFAULT);
        done.setShowBadge(true);
        nm.createNotificationChannel(done);
        NotificationChannel start = new NotificationChannel(CHANNEL_START,
                context.getString(R.string.notification_started_channel), NotificationManager.IMPORTANCE_DEFAULT);
        start.setShowBadge(false);
        nm.createNotificationChannel(start);
        NotificationChannel clipboard = new NotificationChannel(CHANNEL_CLIPBOARD,
                context.getString(R.string.clipboard_channel), NotificationManager.IMPORTANCE_DEFAULT);
        clipboard.setShowBadge(false);
        nm.createNotificationChannel(clipboard);
        NotificationChannel watch = new NotificationChannel(CHANNEL_WATCH,
                context.getString(R.string.clipboard_watch_channel), NotificationManager.IMPORTANCE_MIN);
        watch.setShowBadge(false);
        watch.setSound(null, null);
        nm.createNotificationChannel(watch);
    }

    public static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N || nm.areNotificationsEnabled();
    }

    public static void showClipboard(Context context, String url) {
        if (url == null || url.trim().isEmpty() || !canPost(context)) return;
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra(MainActivity.EXTRA_LINK, url);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(context, 4300, open, pendingFlags());

        Intent download = new Intent(context, MainActivity.class);
        download.putExtra(MainActivity.EXTRA_LINK, url);
        download.putExtra(MainActivity.EXTRA_AUTO_DOWNLOAD, true);
        download.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent downloadPending = PendingIntent.getActivity(context, 4301, download, pendingFlags());

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_CLIPBOARD)
                : new Notification.Builder(context);
        b.setContentTitle(context.getString(R.string.clipboard_notification_title))
                .setContentText(context.getString(R.string.clipboard_notification_body))
                .setStyle(new Notification.BigTextStyle().bigText(url))
                .setSmallIcon(R.drawable.ic_link)
                .setContentIntent(openPending)
                .addAction(android.R.drawable.stat_sys_download,
                        context.getString(R.string.clipboard_notification_download), downloadPending)
                .addAction(android.R.drawable.ic_menu_view,
                        context.getString(R.string.clipboard_notification_open), openPending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(CLIPBOARD_ID, b.build());
    }

    public static void cancelClipboard(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(CLIPBOARD_ID);
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    public static Notification progress(Context context, String title, String text,
                                        int percent, boolean indeterminate) {
        Notification.Builder b = base(context, CHANNEL);
        b.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(100, Math.max(0, Math.min(100, percent)), indeterminate);
        return b.build();
    }

    public static Notification clipboardWatch(Context context) {
        return clipboardWatch(context, true, false);
    }

    public static Notification clipboardWatch(Context context, boolean autoDetect, boolean floating) {
        boolean auto = autoDetect;
        Notification.Builder b = base(context, CHANNEL_WATCH);
        b.setContentTitle(context.getString(auto
                        ? R.string.clipboard_watch_title : R.string.floating_watch_title))
                .setContentText(context.getString(auto
                        ? R.string.clipboard_watch_body : R.string.floating_watch_body))
                .setSmallIcon(auto ? R.drawable.ic_link : R.drawable.ic_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        return b.build();
    }

    public static Notification foreground(Context context, String text) {
        Notification.Builder b = base(context, CHANNEL);
        b.setContentTitle(context.getString(R.string.downloading))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS);
        return b.build();
    }

    public static Notification started(Context context, String title) {
        Notification.Builder b = base(context, CHANNEL_START);
        b.setContentTitle(context.getString(R.string.notification_started))
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(false)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b.setTimeoutAfter(4000);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            //noinspection deprecation
            b.setDefaults(Notification.DEFAULT_SOUND);
        }
        return b.build();
    }

    public static Notification done(Context context, String title) {
        Notification.Builder b = base(context, CHANNEL_DONE);
        b.setContentTitle(context.getString(R.string.notification_complete))
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            //noinspection deprecation
            b.setDefaults(Notification.DEFAULT_SOUND);
        }
        return b.build();
    }

    public static Notification failed(Context context, String title) {
        Notification.Builder b = base(context, CHANNEL_DONE);
        b.setContentTitle(context.getString(R.string.notification_failed))
                .setContentText(title)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setCategory(Notification.CATEGORY_ERROR)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            //noinspection deprecation
            b.setDefaults(Notification.DEFAULT_SOUND);
        }
        return b.build();
    }

    public static Notification captionFailed(Context context, String detail) {
        Notification.Builder b = base(context, CHANNEL_DONE);
        b.setContentTitle(context.getString(R.string.captions_failed_title))
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setCategory(Notification.CATEGORY_ERROR)
                .setAutoCancel(true);
        return b.build();
    }

    public static Notification captionProgress(Context context, String title, int percent) {
        Notification.Builder b = base(context, CHANNEL);
        b.setContentTitle(context.getString(R.string.embedding_caption))
                .setContentText(title == null ? "" : title)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(100, Math.max(0, Math.min(100, percent)), percent <= 0);
        return b.build();
    }

    public static Notification captionDone(Context context, String title) {
        Notification.Builder b = base(context, CHANNEL_DONE);
        b.setContentTitle(context.getString(R.string.caption_embedded))
                .setContentText(title == null ? "" : title)
                .setSmallIcon(R.drawable.ic_check)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            //noinspection deprecation
            b.setDefaults(Notification.DEFAULT_SOUND);
        }
        return b.build();
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder base(Context context, String channel) {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_DOWNLOADS);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(context, 100, intent, flags);
        b.setContentIntent(pending);
        b.setVisibility(Notification.VISIBILITY_PUBLIC);
        return b;
    }

    public static void notifyProgress(Context context, Notification notification) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(PROGRESS_ID, notification);
    }

    public static void notifyDone(Context context, Notification notification) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(DONE_ID, notification);
    }

    public static void notifyCaption(Context context, Notification notification) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(CAPTION_ID, notification);
    }

    public static void notifyCaptionDone(Context context, Notification notification) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.cancel(CAPTION_ID);
        nm.notify(CAPTION_DONE_ID, notification);
    }

    public static void cancelCaption(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(CAPTION_ID);
            nm.cancel(CAPTION_DONE_ID);
        }
    }

    public static void notifyStarted(Context context, Notification notification) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(START_ID, notification);
    }

    public static void cancelProgress(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(PROGRESS_ID);
    }
}
