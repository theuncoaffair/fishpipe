package com.theuncoaffair.fishpipe.download;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.billing.LicenseManager;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.List;

import timber.log.Timber;
import java.util.Locale;

public final class DownloadController {
    public static final String KIND_VIDEO = "video";
    public static final String KIND_HD = "hd";
    public static final String KIND_WM = "wm";
    public static final String KIND_MUSIC = "music";
    public static final String KIND_COVER = "cover";
    public static final String KIND_IMAGES = "images";
    public static final String KIND_IMAGES_AUDIO = "images_audio";

    private DownloadController() {
    }

    public static int enqueueDefaultVideo(Context context, MediaItem item) {
        if (!LicenseManager.allowDownload(context)) return 0;
        Settings settings = AppGraph.get(context).settings();
        String preference = settings.getDefaultVideo();
        boolean captions = settings.isCaptionsEnabled();
        String captionMode = settings.getCaptionMode();
        if (Settings.VIDEO_AUDIO.equals(preference)) {
            int count = enqueue(context, item, KIND_MUSIC, false);
            if (count > 0) return count;
        } else if (Settings.VIDEO_COVER.equals(preference)) {
            int count = enqueue(context, item, KIND_COVER, false);
            if (count > 0) return count;
        } else if (Settings.VIDEO_HD.equals(preference)) {
            int count = enqueue(context, item, KIND_HD, false, captions, captionMode);
            if (count > 0) return count;
        } else if (Settings.VIDEO_STANDARD.equals(preference)) {
            int count = enqueue(context, item, KIND_VIDEO, false, captions, captionMode);
            if (count > 0) return count;
        }
        return enqueue(context, item, KIND_VIDEO, true, captions, captionMode);
    }

    public static int enqueueDefaultAudio(Context context, MediaItem item) {
        if (!LicenseManager.allowDownload(context)) return 0;
        return enqueue(context, item, KIND_MUSIC, false);
    }

    public static int enqueueDefaultPhotos(Context context, MediaItem item) {
        if (!LicenseManager.allowDownload(context)) return 0;
        String preference = AppGraph.get(context).settings().getDefaultPhotos();
        if (Settings.PHOTO_AUDIO.equals(preference)) {
            int count = enqueue(context, item, KIND_MUSIC, false);
            if (count > 0) return count;
        } else if (Settings.PHOTO_SLIDESHOW.equals(preference)) {
            int count = enqueueSlideshow(context, item);
            if (count > 0) return count;
        }
        return enqueue(context, item, KIND_IMAGES, false);
    }

    public static int enqueueImages(Context context, MediaItem item, List<String> images,
                                    boolean withMusic) {
        if (!LicenseManager.allowDownload(context)) return 0;
        if (item == null || images == null || images.isEmpty()) return 0;
        DownloadDb db = new DownloadDb(context);
        int count = 0;
        int index = 1;
        long now = System.currentTimeMillis();
        for (String url : images) {
            if (!MediaItem.notEmpty(url)) continue;
            if (db.hasRemoteUrl(url)) continue;
            DownloadEntry e = base(context, item, DownloadEntry.TYPE_IMAGE, url, now);
            e.title = e.title + " " + index;
            e.thumbUrl = url;
            db.insert(e);
            index++;
            count++;
        }
        if (withMusic && MediaItem.notEmpty(item.musicUrl)) {
            DownloadEntry e = base(context, item, DownloadEntry.TYPE_MUSIC, item.musicUrl, now);
            e.title = MediaItem.notEmpty(item.musicTitle) ? item.musicTitle : e.title + " audio";
            if (MediaItem.notEmpty(item.musicCoverUrl)) e.thumbUrl = item.musicCoverUrl;
            db.insert(e);
            count++;
        }
        if (count > 0) {
            LicenseManager.recordUse(context);
            start(context);
        }
        return count;
    }

    public static int enqueueSlideshow(Context context, MediaItem item) {
        if (item == null || !item.hasImages()) return 0;
        if (!LicenseManager.allowDownload(context)) return 0;
        DownloadDb db = new DownloadDb(context);
        if (db.hasSlideshow(item.id)) return 0;
        long now = System.currentTimeMillis();
        StringBuilder images = new StringBuilder();
        for (String url : item.images) {
            if (!MediaItem.notEmpty(url)) continue;
            if (images.length() > 0) images.append('\n');
            images.append(url);
        }
        if (images.length() == 0) return 0;
        DownloadEntry e = base(context, item, DownloadEntry.TYPE_VIDEO,
                "slideshow:" + item.id + ":" + now, now);
        e.slideshow = true;
        e.slideshowImages = images.toString();
        e.slideshowAudio = MediaItem.notEmpty(item.musicUrl) ? item.musicUrl : "";
        e.title = (MediaItem.notEmpty(item.title) ? item.title : item.id) + " slideshow";
        e.thumbUrl = item.images.get(0);
        db.insert(e);
        LicenseManager.recordUse(context);
        start(context);
        return 1;
    }

    public static int enqueue(Context context, MediaItem item, String kind, boolean preferHd) {
        return enqueue(context, item, kind, preferHd, false, Settings.CAPTION_EMBED);
    }

    public static int enqueue(Context context, MediaItem item, String kind, boolean preferHd,
                              boolean captions, String captionMode) {
        if (!LicenseManager.allowDownload(context)) return 0;
        if (item == null) return 0;
        DownloadDb db = new DownloadDb(context);
        int count = 0;
        long now = System.currentTimeMillis();
        if (KIND_VIDEO.equals(kind) || KIND_HD.equals(kind) || KIND_WM.equals(kind)) {
            String url;
            if (KIND_WM.equals(kind)) {
                url = item.videoWmUrl;
                if (MediaItem.notEmpty(url) && url.equals(item.bestVideo(false))) url = "";
                if (!MediaItem.notEmpty(url)) url = item.bestVideo(false);
            }
            else if (KIND_HD.equals(kind)) {
                url = item.videoHdUrl;
                if (!MediaItem.notEmpty(url) || url.equals(item.videoUrl)) {
                    url = item.bestVideo(true);
                }
            }
            else url = item.bestVideo(preferHd);
            if (MediaItem.notEmpty(url)) {
                DownloadEntry e = base(context, item, DownloadEntry.TYPE_VIDEO, url, now);
                e.quality = KIND_HD.equals(kind)
                        ? DownloadEntry.QUALITY_HD : DownloadEntry.QUALITY_SD;
                if (KIND_HD.equals(kind) && MediaItem.notEmpty(item.videoHdUrl)
                        && !item.videoHdUrl.equals(item.videoUrl)) {
                    e.title = e.title + " HD";
                }
                applyCaption(e, item, captions, captionMode);
                db.insert(e);
                count++;
            }
        } else if (KIND_MUSIC.equals(kind)) {
            if (MediaItem.notEmpty(item.musicUrl)) {
                DownloadEntry e = base(context, item, DownloadEntry.TYPE_MUSIC, item.musicUrl, now);
                if (MediaItem.notEmpty(item.musicTitle)) e.title = item.musicTitle;
                if (MediaItem.notEmpty(item.musicCoverUrl)) e.thumbUrl = item.musicCoverUrl;
                db.insert(e);
                count++;
            }
        } else if (KIND_COVER.equals(kind)) {
            if (MediaItem.notEmpty(item.coverUrl)) {
                DownloadEntry e = base(context, item, DownloadEntry.TYPE_IMAGE, item.coverUrl, now);
                e.title = e.title + " cover";
                db.insert(e);
                count++;
            }
        } else if (KIND_IMAGES.equals(kind) || KIND_IMAGES_AUDIO.equals(kind)) {
            if (item.hasImages()) {
                int index = 1;
                for (String url : item.images) {
                    if (db.hasRemoteUrl(url)) continue;
                    DownloadEntry e = base(context, item, DownloadEntry.TYPE_IMAGE, url, now);
                    e.title = e.title + " " + index;
                    e.thumbUrl = url;
                    db.insert(e);
                    index++;
                    count++;
                }
            }
            if (KIND_IMAGES_AUDIO.equals(kind) && MediaItem.notEmpty(item.musicUrl)) {
                DownloadEntry e = base(context, item, DownloadEntry.TYPE_MUSIC, item.musicUrl, now);
                e.title = MediaItem.notEmpty(item.musicTitle) ? item.musicTitle : e.title + " audio";
                if (MediaItem.notEmpty(item.musicCoverUrl)) e.thumbUrl = item.musicCoverUrl;
                db.insert(e);
                count++;
            }
        }
        if (count > 0) {
            LicenseManager.recordUse(context);
            start(context);
            if (captions && item.hasCaptions()) {
                Ui.toast(context, Settings.CAPTION_SRT.equals(captionMode)
                        ? R.string.downloading_with_captions_srt
                        : R.string.downloading_with_captions_embed);
            }
        }
        return count;
    }

    private static void applyCaption(DownloadEntry entry, MediaItem item, boolean captions,
                                     String captionMode) {
        if (!captions || !item.hasCaptions()) return;
        MediaItem.CaptionTrack track = item.bestCaption(Locale.getDefault());
        if (track == null) return;
        entry.captionMode = Settings.CAPTION_SRT.equals(captionMode)
                ? Settings.CAPTION_SRT : Settings.CAPTION_EMBED;
        entry.captionUrl = track.url;
        entry.captionLanguage = MediaItem.notEmpty(track.language)
                ? track.language : track.languageCode;
        entry.captionAuto = track.autoGenerated;
    }

    public static void cancel(Context context, long rowId) {
        DownloadService.cancel(rowId);
        new DownloadDb(context).setStatus(rowId, DownloadEntry.STATUS_CANCELLED);
        DownloadService.partFile(context, rowId).delete();
    }

    public static void pause(Context context, long rowId) {
        DownloadService.pause(rowId);
        DownloadDb db = new DownloadDb(context);
        DownloadEntry entry = db.get(rowId);
        long bytes = entry == null ? 0 : entry.bytes;
        db.updateProgress(rowId, bytes, 0, DownloadEntry.STATUS_PAUSED);
    }

    public static void resume(Context context, long rowId) {
        DownloadService.clearPause(rowId);
        DownloadDb db = new DownloadDb(context);
        DownloadEntry entry = db.get(rowId);
        if (entry == null) return;
        db.setStatus(rowId, DownloadEntry.STATUS_QUEUED);
        start(context);
    }

    public static void redownload(Context context, long rowId) {
        DownloadDb db = new DownloadDb(context);
        DownloadEntry entry = db.get(rowId);
        if (entry == null || entry.remoteUrl == null || entry.remoteUrl.trim().isEmpty()) return;
        DownloadService.clearPause(rowId);
        DownloadService.partFile(context, rowId).delete();
        db.resetForRedownload(rowId);
        start(context);
    }

    public static void retry(Context context, long rowId) {
        DownloadService.clearPause(rowId);
        DownloadDb db = new DownloadDb(context);
        DownloadEntry entry = db.get(rowId);
        if (entry == null || !(entry.isPaused() || DownloadEntry.STATUS_FAILED.equals(entry.status))) return;
        db.setStatus(rowId, DownloadEntry.STATUS_QUEUED);
        start(context);
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(DownloadService.ACTION_PROCESS);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Timber.tag("DownloadController").w(e, "startForegroundService failed");
            try {
                context.startService(intent);
            } catch (Exception fallback) {
                Timber.tag("DownloadController").w(fallback, "startService fallback failed");
            }
        }
    }

    private static DownloadEntry base(Context context, MediaItem item, String type, String url, long now) {
        if (url != null && url.startsWith("http://")) url = "https://" + url.substring(7);
        DownloadEntry e = new DownloadEntry();
        e.mediaId = item.id;
        if (MediaItem.notEmpty(item.title) && !MediaItem.isSyntheticTitle(item.title)) {
            e.title = item.title;
        } else if (MediaItem.notEmpty(item.id)) {
            String source = MediaItem.notEmpty(item.source) ? item.source.trim() : "tiktok";
            e.title = source + " " + item.id;
        } else {
            e.title = context.getString(R.string.type_video);
        }
        e.author = item.author;
        e.authorAvatar = item.authorAvatar;
        e.caption = item.captionFromTitle();
        e.hashtags = item.hashtagsFromTitle();
        e.pageUrl = item.pageUrl;
        e.type = type;
        e.remoteUrl = url;
        e.thumbUrl = item.coverUrl;
        e.story = item.story;
        e.status = DownloadEntry.STATUS_QUEUED;
        e.createdAt = now;
        e.uploadedAt = item.createTime > 0 ? item.createTime * 1000L : 0;
        return e;
    }
}
