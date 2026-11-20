package com.theuncoaffair.fishpipe.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.theuncoaffair.fishpipe.DownloadSettingsActivity;
import com.theuncoaffair.fishpipe.databinding.DialogDownloadOptionsBinding;
import com.theuncoaffair.fishpipe.databinding.DialogPhotoOptionsBinding;
import com.theuncoaffair.fishpipe.databinding.ItemHeaderThumbBinding;
import com.theuncoaffair.fishpipe.databinding.ItemPhotoPickBinding;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.net.Http;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DownloadOptionsSheet {
    public interface Listener {
        void onEnqueue(String kind, boolean preferHd, boolean captions, String captionMode);

        void onEnqueueImages(List<String> images, boolean withMusic);

        void onEnqueueSlideshow(List<String> images);
    }

    private static final ExecutorService SIZE_POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DownloadOptionsSheet() {
    }

    public static void show(Context context, MediaItem item, Settings settings, Listener listener) {
        show(context, item, settings, listener, null);
    }

    public static void show(Context context, MediaItem item, Settings settings, Listener listener,
                            Runnable onClosed) {
        showInternal(context, item, null, settings, listener, false, onClosed);
    }

    public static void showBatch(Context context, MediaItem item, List<MediaItem> selected,
                                 Settings settings, Listener listener) {
        showBatch(context, item, selected, settings, listener, null);
    }

    public static void showBatch(Context context, MediaItem item, List<MediaItem> selected,
                                 Settings settings, Listener listener, Runnable onClosed) {
        showInternal(context, item, selected, settings, listener, true, onClosed);
    }

    private static void showInternal(Context context, MediaItem item, List<MediaItem> selected,
                                     Settings settings, Listener listener, boolean batch,
                                     Runnable onClosed) {
        if (item == null) {
            if (onClosed != null) onClosed.run();
            return;
        }
        if (!settings.isShowDownloadOptions()) {
            Runnable enqueue = () -> {
                enqueueDefault(item, settings, listener, batch);
                if (onClosed != null) onClosed.run();
            };
            if (batch && selected != null) {
                String preference = settings.getDefaultVideo();
                guardBatch(context, selected, defaultVideoKind(preference),
                        Settings.VIDEO_HD.equals(preference), enqueue, onClosed);
                return;
            }
            List<DownloadEntry> existing = new ArrayList<>(
                    new DownloadDb(context).existingFor(item));
            if (DownloadGuard.confirm(context, existing, enqueue, onClosed)) return;
            enqueue.run();
            return;
        }
        if (!batch && item.hasImages()) {
            showPhotos(context, item, settings, listener, onClosed);
            return;
        }
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogDownloadOptionsBinding binding = DialogDownloadOptionsBinding.inflate(
                LayoutInflater.from(context));
        dialog.setContentView(binding.getRoot());
        CheckBox dontAsk = binding.checkDontAsk;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(sheetWidth(context), ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }

        bindHeader(context, HeaderViews.from(binding), item, selected);

        List<Option> options = new ArrayList<>();
        String video720 = MediaItem.notEmpty(item.videoUrl) ? item.videoUrl
                : MediaItem.notEmpty(item.videoHdUrl) ? item.videoHdUrl : "";
        boolean has720 = batch || MediaItem.notEmpty(video720);
        boolean has1080 = batch || (MediaItem.notEmpty(item.videoHdUrl)
                && !item.videoHdUrl.equals(item.videoUrl));
        Option opt720 = addOption(options, binding.opt720, binding.check720, binding.size720,
                has720, DownloadController.KIND_VIDEO, false, video720);
        Option opt1080 = addOption(options, binding.opt1080, binding.check1080, binding.size1080,
                has1080, DownloadController.KIND_HD, true, item.videoHdUrl);
        Option optAudio = addOption(options, binding.optAudio, binding.checkAudio, binding.sizeAudio,
                batch || item.hasMusic(), DownloadController.KIND_MUSIC, false, item.musicUrl);
        Option optCover = addOption(options, binding.optCover, binding.checkCover, binding.sizeCover,
                batch || item.hasCover(), DownloadController.KIND_COVER, false, item.coverUrl);
        Option slideshow = addOption(options, binding.optSlideshow, binding.checkSlideshow,
                binding.sizeSlideshow, !batch && item.hasImages(),
                item.hasMusic() ? DownloadController.KIND_IMAGES_AUDIO : DownloadController.KIND_IMAGES,
                false, "");
        if (slideshow != null) {
            int count = item.images == null ? 0 : item.images.size();
            TextView size = binding.sizeSlideshow;
            size.setText(context.getString(R.string.sheet_items_count, count));
            TextView title = binding.titleSlideshow;
            if (title != null) {
                title.setText(item.hasMusic()
                        ? R.string.sheet_option_slideshow_audio : R.string.sheet_option_photos);
            }
        }

        boolean captionsAvailable = item.hasCaptions();
        if (batch && selected != null) {
            for (MediaItem selectedItem : selected) {
                if (selectedItem != null && selectedItem.hasCaptions()) {
                    captionsAvailable = true;
                    break;
                }
            }
        }
        final boolean hasCaptions = captionsAvailable;
        View captionsSection = binding.captionsSection;
        View captionsRow = binding.rowCaptionsDownload;
        TextView captionsValue = binding.captionsDownloadValue;
        final boolean[] captionsChoice = {hasCaptions && settings.isCaptionsEnabled()};
        captionsSection.setVisibility(hasCaptions ? View.VISIBLE : View.GONE);
        captionsValue.setText(captionsChoice[0] ? R.string.yes : R.string.no);
        captionsRow.setOnClickListener(v -> Ui.singleChoice(context,
                context.getString(R.string.captions_download),
                new String[]{context.getString(R.string.yes), context.getString(R.string.no)},
                captionsChoice[0] ? 0 : 1, (choice, which) -> {
                    captionsChoice[0] = which == 0;
                    captionsValue.setText(captionsChoice[0] ? R.string.yes : R.string.no);
                    choice.dismiss();
                }));

        String defaultVideo = settings.getDefaultVideo();
        Option preferred = Settings.VIDEO_HD.equals(defaultVideo) ? opt1080
                : Settings.VIDEO_AUDIO.equals(defaultVideo) ? optAudio
                : Settings.VIDEO_COVER.equals(defaultVideo) ? optCover : opt720;
        if (preferred != null && preferred.visible) preferred.selected = true;
        else if (opt720 != null && opt720.visible) opt720.selected = true;
        else if (opt1080 != null && opt1080.visible) opt1080.selected = true;
        else if (optAudio != null && optAudio.visible) optAudio.selected = true;
        else if (optCover != null && optCover.visible) optCover.selected = true;
        else {
            Option fallback = null;
            for (Option option : options) {
                if (!option.visible) continue;
                if (DownloadController.KIND_IMAGES.equals(option.kind)
                        || DownloadController.KIND_IMAGES_AUDIO.equals(option.kind)) {
                    fallback = option;
                    break;
                }
                if (fallback == null && DownloadController.KIND_MUSIC.equals(option.kind)) {
                    fallback = option;
                }
            }
            if (fallback != null) fallback.selected = true;
        }

        TextView download = binding.btnSheetDownload;
        Runnable refresh = () -> {
            boolean any = false;
            boolean video = false;
            for (Option option : options) {
                if (!option.visible) continue;
                drawOption(option);
                if (option.selected) {
                    any = true;
                    if (isVideoKind(option.kind)) video = true;
                }
            }
            download.setEnabled(any);
            download.setAlpha(any ? 1f : 0.45f);
            boolean captionsEnabled = hasCaptions && video;
            captionsSection.setAlpha(captionsEnabled ? 1f : 0.45f);
            captionsRow.setEnabled(captionsEnabled);
            captionsRow.setClickable(captionsEnabled);
        };
        for (Option option : options) {
            if (!option.visible) continue;
            option.card.setOnClickListener(v -> {
                for (Option other : options) {
                    other.selected = other == option;
                }
                refresh.run();
            });
        }
        refresh.run();

        download.setOnClickListener(v -> {
            Option chosen = null;
            for (Option option : options) {
                if (option.visible && option.selected) {
                    chosen = option;
                    break;
                }
            }
            if (chosen == null) {
                Ui.toast(context, R.string.no_media);
                return;
            }
            final Option picked = chosen;
            Runnable enqueue = () -> {
                boolean captions = isVideoKind(picked.kind) && hasCaptions && captionsChoice[0];
                if (hasCaptions && isVideoKind(picked.kind)) {
                    settings.setCaptionsEnabled(captions);
                }
                if (dontAsk != null && dontAsk.isChecked()) {
                    saveVideoChoice(settings, picked);
                    settings.setShowDownloadOptions(false);
                    Ui.toast(context, R.string.download_options_hidden);
                }
                dialog.dismiss();
                listener.onEnqueue(picked.kind, picked.preferHd, captions, Settings.CAPTION_SRT);
            };
            if (batch && selected != null) {
                guardBatch(context, selected, picked.kind, picked.preferHd, enqueue, null);
                return;
            }
            List<DownloadEntry> existing = new ArrayList<>(
                    DownloadGuard.existingForOption(
                            context, item, picked.kind, picked.sizeUrl));
            if (DownloadGuard.confirm(context, existing, enqueue, null)) return;
            enqueue.run();
        });

        binding.btnSheetClose.setOnClickListener(v -> dialog.dismiss());
        binding.btnSheetDefaults.setOnClickListener(v -> {
            context.startActivity(new Intent(context, DownloadSettingsActivity.class));
            if (onClosed != null) dialog.dismiss();
        });
        if (onClosed != null) dialog.setOnDismissListener(ignored -> onClosed.run());

        for (Option option : options) {
            if (option.visible && MediaItem.notEmpty(option.sizeUrl)) {
                loadSize(option.sizeUrl, option.sizeView);
            }
        }

        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setLayout(sheetWidth(context), ViewGroup.LayoutParams.WRAP_CONTENT);
            shown.setGravity(Gravity.BOTTOM);
        }
    }

    private static void bindHeader(Context context, HeaderViews header, MediaItem item,
                                    List<MediaItem> selected) {
        if (selected != null && selected.size() > 1
                && header.thumbScroll != null && header.thumbRow != null) {
            header.thumb.setVisibility(View.GONE);
            header.thumbScroll.setVisibility(View.VISIBLE);
            header.thumbRow.removeAllViews();
            int size = dp(context, 36);
            int overlap = dp(context, 26);
            for (int i = 0; i < selected.size(); i++) {
                ItemHeaderThumbBinding thumb = ItemHeaderThumbBinding.inflate(
                        LayoutInflater.from(context), header.thumbRow, false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        size, ViewGroup.LayoutParams.MATCH_PARENT);
                if (i > 0) params.setMarginStart(-overlap);
                thumb.getRoot().setLayoutParams(params);
                ImageLoader.load(thumb.headerThumb, selected.get(i).coverUrl,
                        R.drawable.ic_image);
                header.thumbRow.addView(thumb.getRoot());
            }
        } else {
            header.thumb.setVisibility(View.VISIBLE);
            if (header.thumbScroll != null) header.thumbScroll.setVisibility(View.GONE);
            ImageLoader.load(header.thumb, item.coverUrl, R.drawable.ic_image);
        }
        ImageLoader.load(header.avatar, item.authorAvatar, R.drawable.ic_app);
        header.authorRow.setVisibility(
                MediaItem.notEmpty(item.author) ? View.VISIBLE : View.GONE);
        header.author.setText(item.author == null ? "" : item.author);
        String dateText = Ui.formatUploadTime(context,
                item.createTime > 0 ? item.createTime * 1000L : 0L);
        if (header.date != null) {
            header.date.setText(dateText);
            header.date.setVisibility(dateText.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (header.dot != null) {
            header.dot.setVisibility(MediaItem.notEmpty(item.author) && !dateText.isEmpty()
                    ? View.VISIBLE : View.GONE);
        }
        String captionText = item.captionFromTitle();
        header.caption.setText(captionText);
        header.caption.setVisibility(captionText.isEmpty() ? View.GONE : View.VISIBLE);
        String hashtagsText = item.hashtagsFromTitle();
        header.hashtags.setText(hashtagsText);
        header.hashtags.setVisibility(hashtagsText.isEmpty() ? View.GONE : View.VISIBLE);
        header.hashtags.setMaxLines(Integer.MAX_VALUE);
        header.hashtags.setEllipsize(null);

        int maxWidth = Math.max(0, context.getResources()
                .getDisplayMetrics().widthPixels - dp(context, 32));
        header.caption.setMaxWidth(maxWidth);
        header.caption.setMaxLines(Integer.MAX_VALUE);
        header.caption.setEllipsize(null);
        header.hashtags.setMaxWidth(maxWidth);
        final boolean[] expanded = {false};
        final boolean[] collapsible = {false};
        header.caption.post(() -> {
            if (header.caption.getLayout() == null) return;
            collapsible[0] = header.caption.getLayout().getLineCount() > 2;
            if (collapsible[0]) {
                header.caption.setMaxLines(2);
                header.caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
        });
        header.caption.setOnClickListener(v -> {
            if (!collapsible[0]) return;
            expanded[0] = !expanded[0];
            header.caption.setMaxLines(expanded[0] ? Integer.MAX_VALUE : 2);
            header.caption.setEllipsize(expanded[0] ? null : android.text.TextUtils.TruncateAt.END);
            if (header.scroll instanceof com.theuncoaffair.fishpipe.util.HalfScreenScrollView) {
                ((com.theuncoaffair.fishpipe.util.HalfScreenScrollView) header.scroll)
                        .setMaxHeightFraction(expanded[0] ? 0.95f : 0.5f);
            }
        });

        if (item.durationSeconds > 0) {
            header.duration.setText(formatDuration(item.durationSeconds));
        } else {
            header.duration.setVisibility(View.GONE);
        }
    }

    private static final class HeaderViews {
        final ImageView thumb;
        final View thumbScroll;
        final LinearLayout thumbRow;
        final ImageView avatar;
        final View authorRow;
        final TextView author;
        final TextView date;
        final View dot;
        final TextView caption;
        final TextView hashtags;
        final View scroll;
        final TextView duration;

        private HeaderViews(ImageView thumb, View thumbScroll, LinearLayout thumbRow,
                            ImageView avatar, View authorRow, TextView author, TextView date,
                            View dot, TextView caption, TextView hashtags, View scroll,
                            TextView duration) {
            this.thumb = thumb;
            this.thumbScroll = thumbScroll;
            this.thumbRow = thumbRow;
            this.avatar = avatar;
            this.authorRow = authorRow;
            this.author = author;
            this.date = date;
            this.dot = dot;
            this.caption = caption;
            this.hashtags = hashtags;
            this.scroll = scroll;
            this.duration = duration;
        }

        static HeaderViews from(DialogDownloadOptionsBinding binding) {
            return new HeaderViews(binding.sheetThumb, binding.sheetThumbScroll,
                    binding.sheetThumbRow, binding.sheetAvatar, binding.sheetAuthorRow,
                    binding.sheetAuthor, binding.sheetDate, binding.sheetDot,
                    binding.sheetCaption, binding.sheetHashtags, binding.sheetScroll,
                    binding.sheetDuration);
        }

        static HeaderViews from(DialogPhotoOptionsBinding binding) {
            return new HeaderViews(binding.sheetThumb, null, null, binding.sheetAvatar,
                    binding.sheetAuthorRow, binding.sheetAuthor, null, null,
                    binding.sheetCaption, binding.sheetHashtags, null, binding.sheetDuration);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int sheetWidth(Context context) {
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        return Math.min(Math.max(0, screen - dp(context, 24)), dp(context, 560));
    }

    private static void showPhotos(Context context, MediaItem item, Settings settings,
                                   Listener listener, Runnable onClosed) {
        final List<String> all = new ArrayList<>();
        for (String url : item.images) {
            if (MediaItem.notEmpty(url)) all.add(url);
        }
        if (all.isEmpty()) {
            Ui.toast(context, R.string.no_media);
            if (onClosed != null) onClosed.run();
            return;
        }
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        DialogPhotoOptionsBinding binding = DialogPhotoOptionsBinding.inflate(
                LayoutInflater.from(context));
        dialog.setContentView(binding.getRoot());
        CheckBox dontAsk = binding.checkDontAsk;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(sheetWidth(context), ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
        bindHeader(context, HeaderViews.from(binding), item, null);
        TextView count = binding.photoCount;
        TextView hint = binding.photoHint;
        GridView grid = binding.photoGrid;
        CheckBox selectAll = binding.photoSelectAll;
        Button download = binding.btnPhotoDownload;
        View photosCard = binding.optPhotos;
        View audioCard = binding.optAudio;
        View slideshowCard = binding.optSlideshow;
        ImageView photosCheck = binding.checkPhotos;
        ImageView audioCheck = binding.checkAudio;
        ImageView slideshowCheck = binding.checkSlideshow;
        final int modePhotos = 0;
        final int modeAudio = 1;
        final int modeSlideshow = 2;
        final boolean canSlideshow = item.hasMusic();
        final int[] mode = {modePhotos};
        final Runnable[] refreshRef = new Runnable[1];
        PhotoPickAdapter adapter = new PhotoPickAdapter(context, all, new PhotoPickAdapter.Listener() {
            @Override
            public void onSelectionChanged(List<String> chosen, int total) {
                if (refreshRef[0] != null) refreshRef[0].run();
            }
        });
        Runnable refresh = () -> {
            boolean photoMode = mode[0] == modePhotos;
            int chosen = adapter.selectedImages().size();
            if (mode[0] == modeAudio) {
                count.setText(R.string.photo_sheet_audio_only);
            } else if (mode[0] == modeSlideshow) {
                count.setText(R.string.photo_sheet_slideshow_all);
            } else {
                count.setText(context.getString(R.string.photo_sheet_selected, chosen, all.size()));
            }
            hint.setText(photoMode ? R.string.photo_sheet_hint : R.string.photo_sheet_hint_locked);
            grid.setAlpha(photoMode ? 1f : 0.5f);
            selectAll.setEnabled(photoMode);
            selectAll.setAlpha(photoMode ? 1f : 0.45f);
            selectAll.setChecked(photoMode && adapter.allSelected());
            download.setEnabled(mode[0] != modePhotos || chosen > 0);
            download.setAlpha(download.isEnabled() ? 1f : 0.45f);
            drawPhotoMode(photosCard, photosCheck, photoMode);
            drawPhotoMode(audioCard, audioCheck, mode[0] == modeAudio);
            drawPhotoMode(slideshowCard, slideshowCheck, mode[0] == modeSlideshow);
        };
        refreshRef[0] = refresh;
        grid.setAdapter(adapter);
        adapter.notifySelection();
        if (Settings.PHOTO_ONLY.equals(settings.getDefaultPhotos())) adapter.selectAll();
        photosCard.setOnClickListener(v -> {
            if (mode[0] == modePhotos) return;
            mode[0] = modePhotos;
            adapter.setSelectionEnabled(true);
            refresh.run();
        });
        audioCard.setVisibility(item.hasMusic() ? View.VISIBLE : View.GONE);
        audioCard.setOnClickListener(v -> {
            if (mode[0] == modeAudio) return;
            mode[0] = modeAudio;
            adapter.setSelectionEnabled(false);
            refresh.run();
        });
        slideshowCard.setAlpha(canSlideshow ? 1f : 0.45f);
        slideshowCard.setOnClickListener(v -> {
            if (!canSlideshow) {
                Ui.toast(context, R.string.slideshow_no_sound);
                return;
            }
            if (mode[0] == modeSlideshow) return;
            mode[0] = modeSlideshow;
            adapter.setSelectionEnabled(false);
            refresh.run();
        });
        selectAll.setOnClickListener(v -> {
            if (mode[0] != modePhotos) {
                selectAll.setChecked(false);
                return;
            }
            if (selectAll.isChecked()) adapter.selectAll();
            else adapter.selectFirstOnly();
            refresh.run();
        });
        download.setOnClickListener(v -> {
            if (mode[0] == modeAudio) {
                Runnable enqueue = () -> {
                    rememberPhotoChoice(context, settings, dontAsk, modeAudio);
                    dialog.dismiss();
                    listener.onEnqueue(DownloadController.KIND_MUSIC, false, false,
                            Settings.CAPTION_EMBED);
                };
                List<com.theuncoaffair.fishpipe.data.DownloadEntry> existing =
                        DownloadGuard.existingForOption(context, item,
                                DownloadController.KIND_MUSIC, item.musicUrl);
                if (DownloadGuard.confirm(context, existing, enqueue, null)) return;
                enqueue.run();
                return;
            }
            if (mode[0] == modeSlideshow) {
                Runnable enqueue = () -> {
                    rememberPhotoChoice(context, settings, dontAsk, modeSlideshow);
                    dialog.dismiss();
                    listener.onEnqueueSlideshow(all);
                };
                List<com.theuncoaffair.fishpipe.data.DownloadEntry> existing =
                        DownloadGuard.existingForSlideshow(context, item);
                if (DownloadGuard.confirm(context, existing, enqueue, null)) return;
                enqueue.run();
                return;
            }
            final List<String> chosen = adapter.selectedImages();
            if (chosen.isEmpty()) {
                Ui.toast(context, R.string.no_items_selected);
                return;
            }
            Runnable enqueue = () -> {
                rememberPhotoChoice(context, settings, dontAsk, modePhotos);
                dialog.dismiss();
                listener.onEnqueueImages(chosen, false);
            };
            List<com.theuncoaffair.fishpipe.data.DownloadEntry> existing =
                    DownloadGuard.existingForOption(context, item,
                            DownloadController.KIND_IMAGES, null);
            if (!existing.isEmpty()
                    && DownloadGuard.confirm(context, existing, enqueue, null)) return;
            enqueue.run();
        });
        TextView photoSize = binding.sizePhotos;
        loadPhotoSizes(all, photoSize);
        if (item.hasMusic()) loadSize(item.musicUrl, binding.sizeAudio);
        binding.btnSheetClose.setOnClickListener(v -> dialog.dismiss());
        binding.btnSheetDefaults.setOnClickListener(v -> {
            context.startActivity(new Intent(context, DownloadSettingsActivity.class));
            if (onClosed != null) dialog.dismiss();
        });
        if (onClosed != null) dialog.setOnDismissListener(ignored -> onClosed.run());
        refresh.run();
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setLayout(sheetWidth(context), ViewGroup.LayoutParams.WRAP_CONTENT);
            shown.setGravity(Gravity.BOTTOM);
        }
    }

    private static void drawPhotoMode(View card, ImageView check, boolean selected) {
        card.setSelected(selected);
        check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        check.setBackgroundResource(selected
                ? R.drawable.bg_check_checked : R.drawable.bg_check_unchecked);
    }

    private static void loadPhotoSizes(List<String> urls, TextView view) {
        SIZE_POOL.execute(() -> {
            long total = 0;
            int count = 0;
            for (String url : urls) {
                long size = Http.contentLength(url);
                if (size > 0) total += size;
                count++;
            }
            if (count == 0) return;
            final long bytes = total;
            final int items = count;
            MAIN.post(() -> view.setText(bytes > 0
                    ? view.getContext().getString(R.string.sheet_items_size, items,
                    Ui.formatBytes(bytes))
                    : view.getContext().getString(R.string.sheet_items_count, items)));
        });
    }

    private static final class PhotoPickAdapter extends BaseAdapter {
        interface Listener {
            void onSelectionChanged(List<String> chosen, int total);
        }

        private final Context context;
        private final List<String> images;
        private final boolean[] selected;
        private final Listener listener;
        private boolean selectionEnabled = true;

        PhotoPickAdapter(Context context, List<String> images, Listener listener) {
            this.context = context;
            this.images = images;
            this.listener = listener;
            this.selected = new boolean[images.size()];
            if (selected.length > 0) selected[0] = true;
        }

        List<String> selectedImages() {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                if (selected[i]) out.add(images.get(i));
            }
            return out;
        }

        void setSelectionEnabled(boolean enabled) {
            if (selectionEnabled == enabled) {
                if (enabled) ensureFirstSelected();
                return;
            }
            selectionEnabled = enabled;
            if (!enabled) {
                for (int i = 0; i < selected.length; i++) selected[i] = false;
            } else {
                ensureFirstSelected();
            }
            notifyDataSetChanged();
            notifySelection();
        }

        void selectAll() {
            if (!selectionEnabled) return;
            for (int i = 0; i < selected.length; i++) selected[i] = true;
            notifyDataSetChanged();
            notifySelection();
        }

        void selectFirstOnly() {
            if (!selectionEnabled) return;
            for (int i = 0; i < selected.length; i++) selected[i] = false;
            if (selected.length > 0) selected[0] = true;
            notifyDataSetChanged();
            notifySelection();
        }

        boolean allSelected() {
            if (selected.length == 0) return false;
            for (boolean value : selected) {
                if (!value) return false;
            }
            return true;
        }

        private void ensureFirstSelected() {
            if (!selectionEnabled || selected.length == 0) return;
            for (boolean value : selected) {
                if (value) return;
            }
            selected[0] = true;
            notifyDataSetChanged();
        }

        private int selectedCount() {
            int count = 0;
            for (boolean value : selected) {
                if (value) count++;
            }
            return count;
        }

        void notifySelection() {
            listener.onSelectionChanged(selectedImages(), images.size());
        }

        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public String getItem(int position) {
            return images.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ItemPhotoPickBinding binding = convertView == null
                    ? ItemPhotoPickBinding.inflate(LayoutInflater.from(context), parent, false)
                    : ItemPhotoPickBinding.bind(convertView);
            View view = binding.getRoot();
            ImageView photo = binding.photo;
            View dim = binding.photoDim;
            View check = binding.photoCheck;
            ImageLoader.load(photo, getItem(position), R.drawable.ic_image);
            boolean chosen = selectionEnabled && selected[position];
            dim.setVisibility(selectionEnabled && !chosen ? View.VISIBLE : View.GONE);
            check.setVisibility(chosen ? View.VISIBLE : View.GONE);
            view.setOnClickListener(v -> {
                if (!selectionEnabled) return;
                if (selected[position] && selectedCount() <= 1) {
                    Ui.toast(context, R.string.photo_sheet_keep_one);
                    return;
                }
                selected[position] = !selected[position];
                notifyDataSetChanged();
                notifySelection();
            });
            return view;
        }
    }

    private static boolean isVideoKind(String kind) {
        return DownloadController.KIND_VIDEO.equals(kind)
                || DownloadController.KIND_HD.equals(kind);
    }

    private static void saveVideoChoice(Settings settings, Option chosen) {
        if (DownloadController.KIND_HD.equals(chosen.kind)) {
            settings.setDefaultVideo(Settings.VIDEO_HD);
        } else if (DownloadController.KIND_MUSIC.equals(chosen.kind)) {
            settings.setDefaultVideo(Settings.VIDEO_AUDIO);
        } else if (DownloadController.KIND_COVER.equals(chosen.kind)) {
            settings.setDefaultVideo(Settings.VIDEO_COVER);
        } else if (DownloadController.KIND_IMAGES.equals(chosen.kind)
                || DownloadController.KIND_IMAGES_AUDIO.equals(chosen.kind)) {
            settings.setDefaultPhotos(Settings.PHOTO_SLIDESHOW);
        } else {
            settings.setDefaultVideo(Settings.VIDEO_STANDARD);
        }
    }

    private static void rememberPhotoChoice(Context context, Settings settings, CheckBox dontAsk,
                                            int mode) {
        if (dontAsk == null || !dontAsk.isChecked()) return;
        if (mode == 1) settings.setDefaultPhotos(Settings.PHOTO_AUDIO);
        else if (mode == 2) settings.setDefaultPhotos(Settings.PHOTO_SLIDESHOW);
        else settings.setDefaultPhotos(Settings.PHOTO_ONLY);
        settings.setShowDownloadOptions(false);
        Ui.toast(context, R.string.download_options_hidden);
    }

    private static void guardBatch(Context context, List<MediaItem> selected, String kind,
                                   boolean preferHd, Runnable enqueue, Runnable onClosed) {
        if (selected == null || selected.isEmpty()) {
            enqueue.run();
            return;
        }
        List<MediaItem> already = new ArrayList<>();
        List<DownloadEntry> existing = new ArrayList<>();
        int validCount = 0;
        for (MediaItem selectedItem : selected) {
            if (selectedItem == null) continue;
            validCount++;
            List<DownloadEntry> found = DownloadGuard.existingForOption(
                    context, selectedItem, kind, urlFor(selectedItem, kind, preferHd));
            if (!found.isEmpty()) {
                already.add(selectedItem);
                existing.addAll(found);
            }
        }
        if (already.isEmpty()) {
            enqueue.run();
            return;
        }
        int existingCount = already.size();
        int newCount = Math.max(0, validCount - existingCount);
        boolean shown = DownloadGuard.confirmBatch(context, existingCount, newCount, existing,
                () -> {
                    selected.removeAll(already);
                    enqueue.run();
                },
                enqueue,
                onClosed);
        if (!shown) enqueue.run();
    }

    private static String urlFor(MediaItem item, String kind, boolean preferHd) {
        if (DownloadController.KIND_MUSIC.equals(kind)) return item.musicUrl;
        if (DownloadController.KIND_COVER.equals(kind)) return item.coverUrl;
        return item.bestVideo(preferHd);
    }

    private static void enqueueDefault(MediaItem item, Settings settings, Listener listener,
                                       boolean batch) {
        if (batch) {
            String preference = settings.getDefaultVideo();
            listener.onEnqueue(defaultVideoKind(preference), Settings.VIDEO_HD.equals(preference),
                    settings.isCaptionsEnabled(), settings.getCaptionMode());
            return;
        }
        if (item.hasImages()) {
            String preference = settings.getDefaultPhotos();
            if (Settings.PHOTO_SLIDESHOW.equals(preference)) {
                listener.onEnqueueSlideshow(item.images);
            } else if (Settings.PHOTO_AUDIO.equals(preference) && item.hasMusic()) {
                listener.onEnqueue(DownloadController.KIND_MUSIC, false, false,
                        Settings.CAPTION_EMBED);
            } else {
                listener.onEnqueueImages(item.images, false);
            }
            return;
        }
        String preference = settings.getDefaultVideo();
        if (Settings.VIDEO_AUDIO.equals(preference) && item.hasMusic()) {
            listener.onEnqueue(DownloadController.KIND_MUSIC, false, false,
                Settings.CAPTION_EMBED);
        } else if (Settings.VIDEO_COVER.equals(preference) && item.hasCover()) {
            listener.onEnqueue(DownloadController.KIND_COVER, false, false,
                    Settings.CAPTION_EMBED);
        } else if (Settings.VIDEO_HD.equals(preference)) {
            if (MediaItem.notEmpty(item.videoHdUrl) && !item.videoHdUrl.equals(item.videoUrl)) {
                listener.onEnqueue(DownloadController.KIND_HD, true,
                        settings.isCaptionsEnabled(), settings.getCaptionMode());
            } else {
                listener.onEnqueue(DownloadController.KIND_VIDEO, true,
                        settings.isCaptionsEnabled(), settings.getCaptionMode());
            }
        } else {
            listener.onEnqueue(DownloadController.KIND_VIDEO, false,
                    settings.isCaptionsEnabled(), settings.getCaptionMode());
        }
    }

    private static String defaultVideoKind(String preference) {
        if (Settings.VIDEO_AUDIO.equals(preference)) return DownloadController.KIND_MUSIC;
        if (Settings.VIDEO_COVER.equals(preference)) return DownloadController.KIND_COVER;
        if (Settings.VIDEO_HD.equals(preference)) return DownloadController.KIND_HD;
        return DownloadController.KIND_VIDEO;
    }

    private static Option addOption(List<Option> options, View card, ImageView check,
                                    TextView size, boolean visible, String kind, boolean preferHd,
                                    String sizeUrl) {
        card.setVisibility(visible ? View.VISIBLE : View.GONE);
        Option option = new Option();
        option.card = card;
        option.check = check;
        option.sizeView = size;
        option.visible = visible;
        option.kind = kind;
        option.preferHd = preferHd;
        option.sizeUrl = sizeUrl == null ? "" : sizeUrl;
        options.add(option);
        return option;
    }

    private static void drawOption(Option option) {
        option.card.setSelected(option.selected);
        option.check.setVisibility(option.selected ? View.VISIBLE : View.INVISIBLE);
        option.check.setBackgroundResource(option.selected
                ? R.drawable.bg_check_checked : R.drawable.bg_check_unchecked);
    }

    private static void loadSize(String url, TextView view) {
        SIZE_POOL.execute(() -> {
            long size = Http.contentLength(url);
            if (size <= 0) return;
            MAIN.post(() -> view.setText(Ui.formatBytes(size)));
        });
    }

    private static String formatDuration(long seconds) {
        long total = Math.max(0, seconds);
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    private static final class Option {
        View card;
        ImageView check;
        TextView sizeView;
        boolean visible;
        boolean selected;
        String kind;
        boolean preferHd;
        String sizeUrl;
    }
}
