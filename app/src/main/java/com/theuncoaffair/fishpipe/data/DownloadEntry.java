package com.theuncoaffair.fishpipe.data;

public final class DownloadEntry {
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CANCELLED = "cancelled";

    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_MUSIC = "music";
    public static final String TYPE_IMAGE = "image";

    public static final String QUALITY_SD = "sd";
    public static final String QUALITY_HD = "hd";

    public long rowId;
    public String mediaId = "";
    public String title = "";
    public String author = "";
    public String authorAvatar = "";
    public String caption = "";
    public String hashtags = "";
    public String pageUrl = "";
    public String type = TYPE_VIDEO;
    public String remoteUrl = "";
    public String localUri = "";
    public String thumbUrl = "";
    public String quality = "";
    public boolean story;
    public boolean slideshow;
    public String slideshowImages = "";
    public String slideshowAudio = "";
    public String captionMode = "";
    public String captionUrl = "";
    public String captionLanguage = "";
    public boolean captionAuto;
    public long size;
    public long bytes;
    public String status = STATUS_QUEUED;
    public int hidden;
    public long createdAt;
    public long uploadedAt;
    public long downloadedAt;

    public DownloadEntry copy() {
        DownloadEntry e = new DownloadEntry();
        e.mediaId = mediaId;
        e.title = title;
        e.author = author;
        e.authorAvatar = authorAvatar;
        e.caption = caption;
        e.hashtags = hashtags;
        e.pageUrl = pageUrl;
        e.type = type;
        e.remoteUrl = remoteUrl;
        e.localUri = localUri;
        e.thumbUrl = thumbUrl;
        e.quality = quality;
        e.story = story;
        e.slideshow = slideshow;
        e.slideshowImages = slideshowImages;
        e.slideshowAudio = slideshowAudio;
        e.captionMode = captionMode;
        e.captionUrl = captionUrl;
        e.captionLanguage = captionLanguage;
        e.captionAuto = captionAuto;
        e.size = size;
        e.bytes = bytes;
        e.status = status;
        e.hidden = hidden;
        e.createdAt = createdAt;
        e.uploadedAt = uploadedAt;
        e.downloadedAt = downloadedAt;
        return e;
    }

    public int progressPercent() {
        if (size <= 0 || bytes <= 0) return status.equals(STATUS_COMPLETED) ? 100 : 0;
        long p = bytes * 100L / size;
        if (p < 0) return 0;
        if (p > 100) return 100;
        return (int) p;
    }

    public boolean isRunning() {
        return STATUS_QUEUED.equals(status) || STATUS_RUNNING.equals(status);
    }

    public boolean isComplete() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isPaused() {
        return STATUS_PAUSED.equals(status);
    }

    public boolean canRetry() {
        return STATUS_FAILED.equals(status) || STATUS_PAUSED.equals(status);
    }
}
