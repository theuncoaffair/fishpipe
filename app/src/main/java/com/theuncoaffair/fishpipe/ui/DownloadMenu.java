package com.theuncoaffair.fishpipe.ui;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.theuncoaffair.fishpipe.AudioPlayerActivity;
import com.theuncoaffair.fishpipe.ImageViewerActivity;
import com.theuncoaffair.fishpipe.PlayerActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.databinding.DialogInputBinding;
import com.theuncoaffair.fishpipe.databinding.DialogMediaInfoBinding;
import com.theuncoaffair.fishpipe.databinding.ItemInfoRowBinding;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.util.FileMetadata;
import com.theuncoaffair.fishpipe.util.MenuDividers;
import com.theuncoaffair.fishpipe.util.MediaProbe;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DownloadMenu {
    private static final ExecutorService RESOLVE_POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Listener {
        void onChanged();
    }

    private DownloadMenu() {
    }

    public static void open(Context context, DownloadEntry entry) {
        open(context, entry, null);
    }

    public static void open(Context context, DownloadEntry entry, Listener listener) {
        if (entry.isRunning()) {
            Ui.toast(context, R.string.file_downloading_wait);
            return;
        }
        if (!entry.isComplete()) {
            Ui.toast(context, R.string.file_not_found);
            return;
        }
        if (!Storage.exists(context, entry)) {
            missing(context, entry, new DownloadDb(context), listener);
            return;
        }
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
            Intent intent = new Intent(context, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_URI, entry.localUri);
            intent.putExtra(ImageViewerActivity.EXTRA_TITLE, entry.title);
            context.startActivity(intent);
        } else if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
            Intent intent = new Intent(context, AudioPlayerActivity.class);
            intent.putExtra(AudioPlayerActivity.EXTRA_URL, entry.localUri);
            intent.putExtra(AudioPlayerActivity.EXTRA_TITLE, entry.title);
            intent.putExtra(AudioPlayerActivity.EXTRA_AUTHOR, entry.author);
            intent.putExtra(AudioPlayerActivity.EXTRA_COVER, entry.thumbUrl);
            intent.putExtra(AudioPlayerActivity.EXTRA_PAGE_URL, entry.pageUrl);
            intent.putExtra(AudioPlayerActivity.EXTRA_CAPTION, entry.caption);
            intent.putExtra(AudioPlayerActivity.EXTRA_HASHTAGS, entry.hashtags);
            context.startActivity(intent);
        } else {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_URL, entry.localUri);
            intent.putExtra(PlayerActivity.EXTRA_TITLE, entry.title);
            context.startActivity(intent);
        }
    }

    public static void show(Context context, View anchor, DownloadEntry entry,
                            DownloadDb db, Listener listener) {
        PopupMenu menu = new PopupMenu(context, anchor);
        int openId = 1;
        int infoId = 2;
        int shareId = 3;
        int linkId = 4;
        int captionId = 5;
        int hashtagsId = 6;
        int qualityId = 7;
        int deleteId = 8;
        boolean music = DownloadEntry.TYPE_MUSIC.equals(entry.type);
        boolean hasLink = MediaItem.notEmpty(entry.pageUrl);
        if (entry.isComplete()) {
            menu.getMenu().add(0, openId, 0, R.string.open);
        }
        menu.getMenu().add(0, infoId, 1, R.string.media_info);
        if (entry.isComplete()) {
            menu.getMenu().add(0, shareId, 2, R.string.share);
        }
        if (hasLink) {
            menu.getMenu().add(0, linkId, 3, R.string.share_link);
        }
        if (!music) {
            menu.getMenu().add(0, captionId, 4, R.string.copy_caption);
            menu.getMenu().add(0, hashtagsId, 5, R.string.copy_hashtags);
        }
        if (hasLink) {
            menu.getMenu().add(0, qualityId, 6, R.string.download_other_quality);
        }
        menu.getMenu().add(1, deleteId, 7, R.string.delete);
        MenuDividers.enable(menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == openId) {
                open(context, entry, listener);
                return true;
            }
            if (item.getItemId() == infoId) {
                showInfo(context, entry);
                return true;
            }
            if (item.getItemId() == captionId) {
                if (entry.caption == null || entry.caption.trim().isEmpty()) {
                    Ui.toast(context, R.string.caption_empty);
                } else {
                    Ui.copy(context, entry.caption, R.string.copied_caption);
                }
                return true;
            }
            if (item.getItemId() == hashtagsId) {
                if (entry.hashtags == null || entry.hashtags.trim().isEmpty()) {
                    Ui.toast(context, R.string.hashtags_empty);
                } else {
                    Ui.copy(context, entry.hashtags, R.string.copied_hashtags);
                }
                return true;
            }
            if (item.getItemId() == linkId) {
                if (MediaItem.notEmpty(entry.pageUrl)) {
                    Ui.shareText(context, entry.pageUrl);
                } else {
                    Ui.toast(context, R.string.share_link_unavailable);
                }
                return true;
            }
            if (item.getItemId() == qualityId) {
                resolveQualities(context, entry, listener);
                return true;
            }
            if (item.getItemId() == shareId) {
                if (!Storage.exists(context, entry)) {
                    missing(context, entry, db, listener);
                    return true;
                }
                Storage.share(context, entry);
                return true;
            }
            if (item.getItemId() == deleteId) {
                PlayerActivity.cancelEmbeddingFor(entry.rowId);
                DeleteDialogs.show(context, false, () -> {
                    if (entry.isRunning()) DownloadController.cancel(context, entry.rowId);
                    Storage.delete(context, entry.localUri);
                    db.delete(entry.rowId);
                    if (listener != null) listener.onChanged();
                }, () -> {
                    if (entry.isRunning()) DownloadController.cancel(context, entry.rowId);
                    db.delete(entry.rowId);
                    if (listener != null) listener.onChanged();
                });
                return true;
            }
            return false;
        });
        menu.show();
    }

    public static void showInfo(Context context, DownloadEntry entry) {
        MediaProbe details = MediaProbe.read(context, entry);
        String fileName = MediaItem.notEmpty(details.fileName)
                ? details.fileName : localFileName(entry.localUri);
        DialogMediaInfoBinding dialogBinding = DialogMediaInfoBinding.inflate(
                LayoutInflater.from(context));
        LinearLayout content = dialogBinding.infoContent;

        addInfoRow(context, content, R.string.info_media_id, valueOrUnknown(context, entry.mediaId));
        addInfoRow(context, content, R.string.info_type, typeLabel(context, entry.type));
        if (DownloadEntry.TYPE_VIDEO.equals(entry.type)) {
            addInfoRow(context, content, R.string.info_quality, qualityLabel(context, entry));
        }
        if (entry.story) addInfoRow(context, content, R.string.story_label,
                context.getString(R.string.yes));
        long size = details.length > 0 ? details.length : entry.size;
        addInfoRow(context, content, R.string.info_length,
                size > 0 ? Ui.formatBytes(size) : context.getString(R.string.unknown));
        addInfoRow(context, content, R.string.info_playing_time, durationLabel(context, details.durationMs));
        addInfoRow(context, content, R.string.info_resolution, resolutionLabel(context, details));
        addInfoRow(context, content, R.string.info_width, numberLabel(context, details.width));
        addInfoRow(context, content, R.string.info_height, numberLabel(context, details.height));
        addInfoRow(context, content, R.string.info_frame_rate, frameRateLabel(context, details.frameRate));
        addInfoRow(context, content, R.string.info_file_extension, upperLabel(context, details.extension));
        addInfoRow(context, content, R.string.info_format, valueOrUnknown(context, details.format));
        addInfoRow(context, content, R.string.info_codec, valueOrUnknown(context, details.codec));
        addInfoRow(context, content, R.string.info_bit_rate, bitRateLabel(context, details.bitRate));
        addInfoRow(context, content, R.string.info_channels, numberLabel(context, details.channels));
        addInfoRow(context, content, R.string.info_sample_rate, sampleRateLabel(context, details.sampleRate));
        AlertDialog[] holder = new AlertDialog[1];
        addCopyRow(context, content, R.string.info_download_location, details.downloadLocation,
                R.string.unknown, R.string.copied_file_path);
        addFileNameRow(context, content, fileName, entry, holder);
        addCopyRow(context, content, R.string.info_file_path, details.filePath,
                R.string.unknown, R.string.copied_file_path);
        addCopyRow(context, content, R.string.info_author, entry.author);
        FileMetadata metadata = details.metadata;
        if (metadata == null && entry.isComplete()) {
            metadata = FileMetadata.saved(
                    Settings.CAPTION_EMBED.equals(entry.captionMode),
                    Settings.CAPTION_SRT.equals(entry.captionMode));
        }
        if (metadata != null) {
            for (FileMetadata.Field field : metadata.fields(context)) {
                addInfoRow(context, content, field.labelRes, field.value, false);
            }
        }
        addInfoRow(context, content, R.string.info_status, statusLabel(context, entry.status));
        if (entry.size > 0) {
            addInfoRow(context, content, R.string.info_progress, entry.progressPercent() + "%");
        }
        if (entry.uploadedAt > 0) {
            addInfoRow(context, content, R.string.info_uploaded,
                    Ui.formatUploadTime(context, entry.uploadedAt));
        }
        if (entry.downloadedAt > 0) {
            addInfoRow(context, content, R.string.info_downloaded, formatDate(entry.downloadedAt));
        }
        if (entry.pageUrl != null && !entry.pageUrl.isEmpty()) {
            addLinkRow(context, content, R.string.info_page, entry.pageUrl);
        }
        addCopyRow(context, content, R.string.info_caption, entry.caption, R.string.none,
                R.string.copied_caption);
        addCopyRow(context, content, R.string.info_hashtags, entry.hashtags, R.string.none,
                R.string.copied_hashtags);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.media_info)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.close, null)
                .create();
        holder[0] = dialog;
        dialog.show();
    }

    public static void showInfo(Context context, MediaItem item) {
        if (item == null) return;
        DialogMediaInfoBinding dialogBinding = DialogMediaInfoBinding.inflate(
                LayoutInflater.from(context));
        LinearLayout content = dialogBinding.infoContent;

        addInfoRow(context, content, R.string.info_media_id, valueOrUnknown(context, item.id));
        addInfoRow(context, content, R.string.info_type,
                context.getString(item.hasImages() ? R.string.type_image : R.string.type_video));
        if (item.story) addInfoRow(context, content, R.string.story_label,
                context.getString(R.string.yes));
        if (item.durationSeconds > 0) {
            addInfoRow(context, content, R.string.info_playing_time,
                    formatSeconds(item.durationSeconds));
        }
        addCopyRow(context, content, R.string.info_author, item.author);
        addInfoRow(context, content, R.string.insights_views, numberValue(item.playCount));
        addInfoRow(context, content, R.string.insights_likes, numberValue(item.diggCount));
        addInfoRow(context, content, R.string.insights_comments, numberValue(item.commentCount));
        addInfoRow(context, content, R.string.insights_shares, numberValue(item.shareCount));
        if (item.pageUrl != null && !item.pageUrl.isEmpty()) {
            addLinkRow(context, content, R.string.info_page, item.pageUrl);
        }
        addCopyRow(context, content, R.string.info_caption,
                item.captionFromTitle(), R.string.none, R.string.copied_caption);
        addCopyRow(context, content, R.string.info_hashtags,
                item.hashtagsFromTitle(), R.string.none, R.string.copied_hashtags);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.media_info)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private static String numberValue(long value) {
        return value > 0 ? String.valueOf(value) : "0";
    }

    private static String formatSeconds(long seconds) {
        long total = Math.max(0, seconds);
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    private static void addInfoRow(Context context, LinearLayout parent, int labelRes, String value) {
        addInfoRow(context, parent, labelRes, value, false);
    }

    private static void addInfoRow(Context context, LinearLayout parent, int labelRes,
                                   String value, boolean copyOnTap) {
        addInfoRow(context, parent, labelRes, value, copyOnTap, R.string.copied);
    }

    private static void addInfoRow(Context context, LinearLayout parent, int labelRes,
                                   String value, boolean copyOnTap, int copyLabelRes) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(
                LayoutInflater.from(context), parent, false);
        row.infoLabel.setText(labelRes);
        String shown = value == null || value.isEmpty()
                ? context.getString(R.string.unknown) : value;
        row.infoValue.setText(shown);
        row.infoValue.setTextIsSelectable(!copyOnTap);
        if (copyOnTap) {
            View.OnClickListener copy = v -> Ui.copy(context, shown, copyLabelRes);
            row.getRoot().setOnClickListener(copy);
            row.infoValue.setOnClickListener(copy);
        }
        parent.addView(row.getRoot());
    }

    private static void addCopyRow(Context context, LinearLayout parent, int labelRes, String value) {
        addCopyRow(context, parent, labelRes, value, R.string.unknown);
    }

    private static void addCopyRow(Context context, LinearLayout parent, int labelRes,
                                   String value, int emptyRes) {
        addCopyRow(context, parent, labelRes, value, emptyRes, R.string.copied);
    }

    private static void addCopyRow(Context context, LinearLayout parent, int labelRes,
                                   String value, int emptyRes, int copyLabelRes) {
        String raw = value == null ? "" : value.trim();
        String shown = raw.isEmpty() ? context.getString(emptyRes) : value;
        addInfoRow(context, parent, labelRes, shown, !raw.isEmpty(), copyLabelRes);
    }

    private static void addFileNameRow(Context context, LinearLayout parent, String value,
                                       DownloadEntry entry, AlertDialog[] holder) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(
                LayoutInflater.from(context), parent, false);
        row.infoLabel.setText(R.string.info_file_name);
        row.infoValue.setText(value == null || value.isEmpty()
                ? context.getString(R.string.unknown) : value);
        row.infoValue.setTextColor(ThemeManager.attrColor(context, R.attr.colorPrimary));
        View.OnClickListener rename = v -> showRenameDialog(context, entry, holder);
        row.getRoot().setOnClickListener(rename);
        row.infoValue.setOnClickListener(rename);
        View.OnLongClickListener copy = v -> {
            Ui.copy(context, value == null ? "" : value, R.string.copied_filename);
            return true;
        };
        row.getRoot().setLongClickable(true);
        row.infoValue.setLongClickable(true);
        row.getRoot().setOnLongClickListener(copy);
        row.infoValue.setOnLongClickListener(copy);
        parent.addView(row.getRoot());
    }

    private static void showRenameDialog(Context context, DownloadEntry entry, AlertDialog[] holder) {
        String current = Storage.displayName(context, entry.localUri);
        if (current == null || current.trim().isEmpty()) current = localFileName(entry.localUri);
        DialogInputBinding inputBinding = DialogInputBinding.inflate(
                LayoutInflater.from(context));
        TextInputEditText input = inputBinding.input;
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(current);
        input.setSelectAllOnFocus(true);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.rename_file)
                .setView(inputBinding.getRoot())
                .setPositiveButton(R.string.rename, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                    v -> confirmRename(context, entry, input.getText().toString(), dialog, holder));
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    confirmRename(context, entry, input.getText().toString(), dialog, holder);
                    return true;
                }
                return false;
            });
        });
        dialog.show();
    }

    private static void confirmRename(Context context, DownloadEntry entry, String name,
                                      AlertDialog editDialog, AlertDialog[] holder) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            Ui.toast(context, R.string.rename_empty);
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.rename_confirm_title)
                .setMessage(context.getString(R.string.rename_confirm_body, clean))
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    String updated = Storage.rename(context, entry, clean);
                    if (updated == null) {
                        Ui.toast(context, R.string.rename_failed);
                        return;
                    }
                    editDialog.dismiss();
                    if (holder[0] != null) holder[0].dismiss();
                    Ui.toast(context, R.string.rename_success);
                    showInfo(context, entry);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void addLinkRow(Context context, LinearLayout parent, int labelRes, String value) {
        ItemInfoRowBinding row = ItemInfoRowBinding.inflate(
                LayoutInflater.from(context), parent, false);
        row.infoLabel.setText(labelRes);
        SpannableString linked = new SpannableString(value);
        Linkify.addLinks(linked, Linkify.WEB_URLS);
        row.infoValue.setText(linked);
        row.infoValue.setLinkTextColor(ThemeManager.attrColor(context, R.attr.colorPrimary));
        row.infoValue.setMovementMethod(LinkMovementMethod.getInstance());
        View.OnLongClickListener copy = v -> {
            Ui.copy(context, value, R.string.copied_link);
            return true;
        };
        row.getRoot().setLongClickable(true);
        row.infoValue.setLongClickable(true);
        row.getRoot().setOnLongClickListener(copy);
        row.infoValue.setOnLongClickListener(copy);
        parent.addView(row.getRoot());
    }

    public static void showOptions(Context context, DownloadEntry entry, Listener listener) {
        resolveQualities(context, entry, listener);
    }

    private static void resolveQualities(Context context, DownloadEntry entry, Listener listener) {
        if (entry.pageUrl == null || entry.pageUrl.trim().isEmpty()) {
            Ui.toast(context, R.string.page_link_missing);
            return;
        }
        Ui.toast(context, R.string.resolving_link);
        String page = entry.pageUrl;
        Context app = context.getApplicationContext();
        RESOLVE_POOL.execute(() -> {
            MediaItem item = null;
            try {
                item = AppGraph.get(app).resolveRepository().resolve(page);
            } catch (Exception ignored) {
            }
            MediaItem resolved = item;
            MAIN.post(() -> {
                if (resolved == null) {
                    Ui.toast(context, R.string.resolve_failed);
                    return;
                }
                DownloadOptionsSheet.show(context, resolved, AppGraph.get(context).settings(),
                        new DownloadOptionsSheet.Listener() {
                            @Override
                            public void onEnqueue(String kind, boolean preferHd, boolean captions,
                                          String captionMode) {
                                int count = DownloadController.enqueue(context, resolved, kind, preferHd, captions, captionMode);
                                if (count > 0) {
                                    Ui.downloadStarted(context, context.getString(R.string.download_queued_count, count));
                                }
                                if (listener != null) listener.onChanged();
                            }

                            @Override
                            public void onEnqueueImages(List<String> images, boolean withMusic) {
                                int count = DownloadController.enqueueImages(context, resolved, images, withMusic);
                                if (count > 0) {
                                    Ui.downloadStarted(context, context.getString(R.string.download_queued_count, count));
                                }
                                if (listener != null) listener.onChanged();
                            }

                            @Override
                            public void onEnqueueSlideshow(List<String> images) {
                                int count = DownloadController.enqueueSlideshow(context, resolved);
                                if (count > 0) {
                                    Ui.downloadStarted(context, context.getString(R.string.download_queued_count, count));
                                }
                                if (listener != null) listener.onChanged();
                            }
                        });
            });
        });
    }

    private static void missing(Context context, DownloadEntry entry, DownloadDb db, Listener listener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.file_missing_title)
                .setMessage(R.string.file_missing_body)
                .setPositiveButton(R.string.redownload, (dialog, which) -> {
                    DownloadController.redownload(context, entry.rowId);
                    if (listener != null) listener.onChanged();
                })
                .setNegativeButton(R.string.remove_from_list, (dialog, which) -> {
                    db.delete(entry.rowId);
                    if (listener != null) listener.onChanged();
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private static String localFileName(String localUri) {
        if (localUri == null || localUri.trim().isEmpty()) return "";
        try {
            android.net.Uri uri = android.net.Uri.parse(localUri);
            String path = uri.getLastPathSegment();
            if (path == null || path.trim().isEmpty()) path = uri.getPath();
            if (path == null) return "";
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        } catch (Exception e) {
            return "";
        }
    }

    private static String valueOrUnknown(Context context, String value) {
        return value == null || value.trim().isEmpty()
                ? context.getString(R.string.unknown) : value;
    }

    private static String typeLabel(Context context, String type) {
        if (DownloadEntry.TYPE_MUSIC.equals(type)) return context.getString(R.string.type_music);
        if (DownloadEntry.TYPE_IMAGE.equals(type)) return context.getString(R.string.type_image);
        return context.getString(R.string.type_video);
    }

    private static String qualityLabel(Context context, DownloadEntry entry) {
        if (DownloadEntry.QUALITY_HD.equals(entry.quality)) {
            return context.getString(R.string.quality_hd);
        }
        if (DownloadEntry.QUALITY_SD.equals(entry.quality)) {
            return context.getString(R.string.quality_sd);
        }
        String title = entry.title == null ? "" : entry.title.trim();
        if (title.endsWith(" HD")) return context.getString(R.string.quality_hd);
        return context.getString(R.string.unknown);
    }

    private static String statusLabel(Context context, String status) {
        if (DownloadEntry.STATUS_QUEUED.equals(status)) return context.getString(R.string.queued);
        if (DownloadEntry.STATUS_RUNNING.equals(status)) return context.getString(R.string.downloading);
        if (DownloadEntry.STATUS_PAUSED.equals(status)) return context.getString(R.string.status_paused);
        if (DownloadEntry.STATUS_COMPLETED.equals(status)) return context.getString(R.string.status_completed);
        if (DownloadEntry.STATUS_FAILED.equals(status)) return context.getString(R.string.status_failed);
        if (DownloadEntry.STATUS_CANCELLED.equals(status)) return context.getString(R.string.status_cancelled);
        return context.getString(R.string.unknown);
    }

    private static String durationLabel(Context context, long millis) {
        if (millis <= 0) return context.getString(R.string.unknown);
        long total = Math.max(0, millis) / 1000L;
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private static String resolutionLabel(Context context, MediaProbe details) {
        if (details.width <= 0 || details.height <= 0) return context.getString(R.string.unknown);
        return details.width + " x " + details.height;
    }

    private static String numberLabel(Context context, int value) {
        return value > 0 ? String.valueOf(value) : context.getString(R.string.unknown);
    }

    private static String frameRateLabel(Context context, float value) {
        if (value <= 0) return context.getString(R.string.unknown);
        String text = String.format(Locale.US, "%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text + " fps";
    }

    private static String upperLabel(Context context, String value) {
        return value == null || value.isEmpty()
                ? context.getString(R.string.unknown) : value.toUpperCase(Locale.US);
    }

    private static String bitRateLabel(Context context, long value) {
        if (value <= 0) return context.getString(R.string.unknown);
        if (value >= 1_000_000L) {
            return String.format(Locale.US, "%.2f Mbps", value / 1_000_000d);
        }
        return Math.round(value / 1000d) + " kbps";
    }

    private static String sampleRateLabel(Context context, int value) {
        if (value <= 0) return context.getString(R.string.unknown);
        if (value >= 1000) return String.format(Locale.US, "%.1f kHz", value / 1000d);
        return value + " Hz";
    }

    private static String formatDate(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
