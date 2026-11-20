package com.theuncoaffair.fishpipe.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.databinding.DialogBatchExistingBinding;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DownloadGuard {
    private DownloadGuard() {
    }

    public static boolean confirm(Context context, MediaItem item, Runnable next) {
        return confirm(context, item, next, null);
    }

    public static boolean confirm(Context context, MediaItem item, Runnable next,
                                  Runnable onDismissWithoutNext) {
        if (context == null || item == null) return false;
        return confirm(context, existingFor(context, item), next, onDismissWithoutNext);
    }

    public static List<DownloadEntry> existingFor(Context context, MediaItem item) {
        if (context == null || item == null) return new ArrayList<>();
        return filterExisting(context, new DownloadDb(context).existingFor(item));
    }

    private static List<DownloadEntry> filterVideoQuality(List<DownloadEntry> entries,
                                                          MediaItem item, String kind) {
        List<DownloadEntry> out = new ArrayList<>();
        boolean wantHd = DownloadController.KIND_HD.equals(kind);
        for (DownloadEntry entry : entries) {
            if (entry == null) continue;
            String quality = videoQuality(entry, item);
            if (quality.isEmpty()
                    || (wantHd ? DownloadEntry.QUALITY_HD.equals(quality)
                    : DownloadEntry.QUALITY_SD.equals(quality))) {
                out.add(entry);
            }
        }
        return out;
    }

    private static String videoQuality(DownloadEntry entry, MediaItem item) {
        if (DownloadEntry.QUALITY_HD.equals(entry.quality)
                || DownloadEntry.QUALITY_SD.equals(entry.quality)) return entry.quality;
        String title = entry.title == null ? "" : entry.title.trim();
        if (title.endsWith(" HD")) return DownloadEntry.QUALITY_HD;
        if (item != null && MediaItem.notEmpty(item.videoHdUrl)
                && !item.videoHdUrl.equals(item.videoUrl)
                && item.videoHdUrl.equals(entry.remoteUrl)) {
            return DownloadEntry.QUALITY_HD;
        }
        if (item != null && MediaItem.notEmpty(item.videoUrl)
                && item.videoUrl.equals(entry.remoteUrl)) {
            return DownloadEntry.QUALITY_SD;
        }
        return "";
    }

    private static List<DownloadEntry> filterExisting(Context context,
                                                       List<DownloadEntry> entries) {
        List<DownloadEntry> out = new ArrayList<>();
        if (context == null || entries == null) return out;
        Set<Long> seen = new HashSet<>();
        for (DownloadEntry entry : entries) {
            if (entry == null) continue;
            if (entry.rowId > 0 && !seen.add(entry.rowId)) continue;
            if (entry.isRunning() || Storage.exists(context, entry)) out.add(entry);
        }
        return out;
    }

    public static List<DownloadEntry> existingForUrls(Context context, List<String> urls) {
        List<DownloadEntry> out = new ArrayList<>();
        if (context == null || urls == null || urls.isEmpty()) return out;
        DownloadDb db = new DownloadDb(context);
        for (String url : urls) {
            if (!MediaItem.notEmpty(url)) continue;
            out.addAll(db.listByRemoteUrl(url));
        }
        return filterExisting(context, out);
    }

    public static List<DownloadEntry> existingForOption(Context context, MediaItem item,
                                                        String kind, String remoteUrl) {
        List<DownloadEntry> out = new ArrayList<>();
        if (context == null || item == null) return out;
        DownloadDb db = new DownloadDb(context);
        if (DownloadController.KIND_IMAGES.equals(kind)
                || DownloadController.KIND_IMAGES_AUDIO.equals(kind)) {
            out.addAll(db.photosFor(item));
            return filterExisting(context, out);
        }
        if (DownloadController.KIND_VIDEO.equals(kind)
                || DownloadController.KIND_HD.equals(kind)) {
            List<DownloadEntry> byId = db.listByMediaIdAndType(item.id, DownloadEntry.TYPE_VIDEO);
            if (!byId.isEmpty()) {
                return filterExisting(context, filterVideoQuality(byId, item, kind));
            }
        }
        if (MediaItem.notEmpty(remoteUrl)) return filterExisting(context,
                db.listByRemoteUrl(remoteUrl));
        return out;
    }

    public static List<DownloadEntry> existingForSlideshow(Context context, MediaItem item) {
        List<DownloadEntry> out = new ArrayList<>();
        if (context == null || item == null) return out;
        DownloadDb db = new DownloadDb(context);
        if (db.hasSlideshow(item.id)) {
            out.addAll(db.listByMediaIdAndType(item.id, DownloadEntry.TYPE_VIDEO));
        }
        return filterExisting(context, out);
    }

    public static boolean confirm(Context context, List<DownloadEntry> existing, Runnable next,
                                  Runnable onDismissWithoutNext) {
        if (context == null || existing == null || next == null) return false;
        List<DownloadEntry> found = filterExisting(context, existing);
        if (found.isEmpty()) return false;
        DownloadEntry first = found.get(0);
        boolean[] proceeded = {false};
        AlertDialog dialog = new MaterialAlertDialogBuilder(dialogContext(context))
                .setTitle(R.string.already_downloaded_title)
                .setMessage(R.string.already_downloaded_body)
                .setPositiveButton(R.string.redownload_replace, (d, which) -> {
                    proceeded[0] = true;
                    deleteEntries(context, found);
                    next.run();
                })
                .setNegativeButton(R.string.show_file, (d, which) ->
                        DownloadMenu.open(context, first, () -> { }))
                .setNeutralButton(R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if (!proceeded[0] && onDismissWithoutNext != null) {
                        onDismissWithoutNext.run();
                    }
                })
                .create();
        showDialog(context, dialog, onDismissWithoutNext, proceeded);
        return true;
    }

    public static boolean confirmBatch(Context context, int existingCount, int newCount,
                                       List<DownloadEntry> existing, Runnable onSkip,
                                       Runnable onRedownload, Runnable onDismissWithoutNext) {
        if (context == null || existing == null || existing.isEmpty()) return false;
        if (existingCount <= 0) return false;
        List<DownloadEntry> found = filterExisting(context, existing);
        if (found.isEmpty()) return false;
        Context dialogContext = dialogContext(context);
        DialogBatchExistingBinding binding = DialogBatchExistingBinding.inflate(
                LayoutInflater.from(dialogContext));
        View content = binding.getRoot();
        TextView body = binding.batchExistingBody;
        CheckBox skip = binding.batchSkipExisting;
        body.setText(context.getString(R.string.batch_existing_body, existingCount));
        skip.setText(context.getString(R.string.batch_skip_existing, existingCount));
        skip.setVisibility(newCount > 0 ? View.VISIBLE : View.GONE);
        boolean[] proceeded = {false};
        AlertDialog dialog = new MaterialAlertDialogBuilder(dialogContext)
                .setTitle(R.string.batch_existing_title)
                .setView(content)
                .setPositiveButton(context.getString(R.string.batch_redownload, existingCount),
                        (d, which) -> {
                            proceeded[0] = true;
                            if (skip.isChecked()) {
                                if (onSkip != null) onSkip.run();
                            } else {
                                deleteEntries(context, found);
                                if (onRedownload != null) onRedownload.run();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if (!proceeded[0] && onDismissWithoutNext != null) {
                        onDismissWithoutNext.run();
                    }
                })
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive == null) return;
            skip.setOnCheckedChangeListener((buttonView, checked) -> positive.setText(checked
                    ? context.getString(R.string.batch_download_new, newCount)
                    : context.getString(R.string.batch_redownload, existingCount)));
        });
        showDialog(context, dialog, onDismissWithoutNext, proceeded);
        return true;
    }

    private static Context dialogContext(Context context) {
        return hasActivityContext(context) ? context
                : new ContextThemeWrapper(context, ThemeManager.isLight(context)
                        ? R.style.Theme_FishPipe_Dialog_Light : R.style.Theme_FishPipe_Dialog);
    }

    private static void deleteEntries(Context context, List<DownloadEntry> entries) {
        DownloadDb db = new DownloadDb(context);
        for (DownloadEntry entry : entries) {
            if (entry.isRunning()) DownloadController.cancel(context, entry.rowId);
            Storage.delete(context, entry.localUri);
            db.delete(entry.rowId);
        }
    }

    private static void showDialog(Context context, AlertDialog dialog,
                                   Runnable onDismissWithoutNext, boolean[] proceeded) {
        try {
            if (!hasActivityContext(context) && android.provider.Settings.canDrawOverlays(context)) {
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                }
            }
            dialog.show();
        } catch (Exception e) {
            if (!proceeded[0] && onDismissWithoutNext != null) {
                onDismissWithoutNext.run();
            }
            Ui.toast(context, R.string.already_downloaded_title);
        }
    }

    private static boolean hasActivityContext(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return true;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return false;
    }
}
