package com.theuncoaffair.fishpipe.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.PopupMenu;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThumbnailMenu {
    private static final int OPEN = 1;
    private static final int SHARE = 2;
    private static final int INFO = 3;
    private static final int CAPTION = 4;
    private static final int HASHTAGS = 5;
    private static final int LINK = 6;
    private static final int QUALITY = 7;
    private static final int DELETE = 8;

    private static final ExecutorService RESOLVE_POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ThumbnailMenu() {
    }

    public static void showEntry(Context context, View anchor, DownloadEntry entry, Runnable onChanged) {
        DownloadMenu.show(context, anchor, entry, new DownloadDb(context),
                onChanged == null ? null : onChanged::run);
    }

    public static void showMedia(Context context, View anchor, MediaItem item,
                                 Runnable onOpen, Runnable onRemove) {
        if (item == null) return;
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, OPEN, 0, R.string.open);
        menu.getMenu().add(0, SHARE, 1, R.string.share);
        menu.getMenu().add(0, INFO, 2, R.string.media_info);
        menu.getMenu().add(0, CAPTION, 3, R.string.copy_caption);
        menu.getMenu().add(0, HASHTAGS, 4, R.string.copy_hashtags);
        if (MediaItem.notEmpty(item.pageUrl)) {
            menu.getMenu().add(0, LINK, 5, R.string.copy_link);
        }
        menu.getMenu().add(0, QUALITY, 6, R.string.download_other_quality);
        menu.getMenu().add(0, DELETE, 7, R.string.delete);
        menu.setOnMenuItemClickListener(choice -> {
            switch (choice.getItemId()) {
                case OPEN:
                    if (onOpen != null) onOpen.run();
                    return true;
                case SHARE:
                    shareLink(context, item);
                    return true;
                case INFO:
                    DownloadMenu.showInfo(context, item);
                    return true;
                case CAPTION:
                    copyCaption(context, item);
                    return true;
                case HASHTAGS:
                    copyHashtags(context, item);
                    return true;
                case LINK:
                    copyLink(context, item);
                    return true;
                case QUALITY:
                    showOptions(context, item);
                    return true;
                case DELETE:
                    confirmRemove(context, onRemove);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private static void copyCaption(Context context, MediaItem item) {
        String caption = item.captionFromTitle();
        if (caption.isEmpty()) {
            Ui.toast(context, R.string.caption_empty);
        } else {
            Ui.copy(context, caption, R.string.copied_caption);
        }
    }

    private static void copyHashtags(Context context, MediaItem item) {
        String hashtags = item.hashtagsFromTitle();
        if (hashtags.isEmpty()) {
            Ui.toast(context, R.string.hashtags_empty);
        } else {
            Ui.copy(context, hashtags, R.string.copied_hashtags);
        }
    }

    private static void copyLink(Context context, MediaItem item) {
        if (!MediaItem.notEmpty(item.pageUrl)) {
            Ui.toast(context, R.string.no_media);
            return;
        }
        Ui.copy(context, item.pageUrl, R.string.copied_link);
    }

    private static void shareLink(Context context, MediaItem item) {
        String link = item.bestVideo(false);
        if (!MediaItem.notEmpty(link)) {
            Ui.toast(context, R.string.no_media);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        if (MediaItem.notEmpty(item.title)) intent.putExtra(Intent.EXTRA_SUBJECT, item.title);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)));
    }

    private static void confirmRemove(Context context, Runnable onRemove) {
        Ui.confirm(context, context.getString(R.string.batch_remove_title),
                context.getString(R.string.batch_remove_body),
                context.getString(R.string.remove_from_list), (dialog, which) -> {
                    if (onRemove != null) onRemove.run();
                });
    }

    private static void showOptions(Context context, MediaItem item) {
        if (!needsResolution(item)) {
            openOptions(context, item);
            return;
        }
        if (!MediaItem.notEmpty(item.pageUrl)) {
            if (item.hasContent() || item.hasMusic() || item.hasCover()) openOptions(context, item);
            else Ui.toast(context, R.string.no_media);
            return;
        }
        Ui.toast(context, R.string.resolving_link);
        String page = item.pageUrl;
        Context app = context.getApplicationContext();
        RESOLVE_POOL.execute(() -> {
            MediaItem resolved = null;
            try {
                resolved = AppGraph.get(app).resolveRepository().resolve(page);
            } catch (Exception ignored) {
            }
            MediaItem target = resolved != null && resolved.hasContent() ? resolved : item;
            MAIN.post(() -> {
                if (!target.hasContent() && !target.hasMusic() && !target.hasCover()) {
                    Ui.toast(context, R.string.resolve_failed);
                } else {
                    openOptions(context, target);
                }
            });
        });
    }

    private static boolean needsResolution(MediaItem item) {
        return !item.hasVideo() || !item.hasMusic() || !item.hasCover()
                || !MediaItem.notEmpty(item.videoHdUrl);
    }

    private static void openOptions(Context context, MediaItem item) {
        DownloadOptionsSheet.show(context, item, AppGraph.get(context).settings(),
                new DownloadOptionsSheet.Listener() {
                    @Override
                    public void onEnqueue(String kind, boolean preferHd, boolean captions,
                                          String captionMode) {
                        queued(context, DownloadController.enqueue(context, item, kind, preferHd, captions, captionMode));
                    }

                    @Override
                    public void onEnqueueImages(List<String> images, boolean withMusic) {
                        queued(context, DownloadController.enqueueImages(context, item, images, withMusic));
                    }

                    @Override
                    public void onEnqueueSlideshow(List<String> images) {
                        queued(context, DownloadController.enqueueSlideshow(context, item));
                    }
                });
    }

    private static void queued(Context context, int count) {
        if (count > 0) Ui.downloadStarted(context, context.getString(R.string.download_queued_count, count));
        else Ui.toast(context, R.string.no_media);
    }
}
