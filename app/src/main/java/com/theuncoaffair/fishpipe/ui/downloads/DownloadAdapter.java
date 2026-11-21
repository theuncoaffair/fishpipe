package com.theuncoaffair.fishpipe.ui.downloads;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ItemDownloadBinding;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.Holder> {
    public interface Listener {
        void onOpen(DownloadEntry entry);

        void onLongPress(DownloadEntry entry);

        void onToggle(DownloadEntry entry);

        void onCancel(DownloadEntry entry);

        void onPause(DownloadEntry entry);

        void onRetry(DownloadEntry entry);

        void onMenu(View anchor, DownloadEntry entry);
    }

    private final List<DownloadEntry> items = new ArrayList<>();
    private final Listener listener;
    private Set<Long> selected = Collections.emptySet();
    private boolean selectionMode;

    public DownloadAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<DownloadEntry> next) {
        List<DownloadEntry> incoming = next == null ? Collections.emptyList() : new ArrayList<>(next);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return incoming.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return items.get(oldPosition).rowId == incoming.get(newPosition).rowId;
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return same(items.get(oldPosition), incoming.get(newPosition));
            }
        });
        items.clear();
        items.addAll(incoming);
        result.dispatchUpdatesTo(this);
    }

    public void setSelection(boolean mode, Set<Long> selectedIds) {
        selectionMode = mode;
        selected = selectedIds == null ? Collections.emptySet() : selectedIds;
        notifyItemRangeChanged(0, items.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDownloadBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String qualityLabel(Context context, DownloadEntry entry) {
        if (!DownloadEntry.TYPE_VIDEO.equals(entry.type)) return "";
        if (DownloadEntry.QUALITY_HD.equals(entry.quality)) {
            return context.getString(R.string.quality_hd);
        }
        if (DownloadEntry.QUALITY_SD.equals(entry.quality)) {
            return context.getString(R.string.quality_sd);
        }
        String title = entry.title == null ? "" : entry.title.trim();
        return title.endsWith(" HD") ? context.getString(R.string.quality_hd) : "";
    }

    private static boolean same(DownloadEntry a, DownloadEntry b) {
        return a.rowId == b.rowId
                && a.status.equals(b.status)
                && a.bytes == b.bytes
                && a.size == b.size
                && a.createdAt == b.createdAt
                && a.story == b.story
                && Objects.equals(a.title, b.title)
                && Objects.equals(a.author, b.author)
                && Objects.equals(a.thumbUrl, b.thumbUrl)
                && Objects.equals(a.localUri, b.localUri)
                && Objects.equals(a.captionMode, b.captionMode);
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemDownloadBinding binding;

        Holder(ItemDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DownloadEntry entry) {
            Context context = binding.getRoot().getContext();
            binding.check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            binding.check.setChecked(selected.contains(entry.rowId));
            binding.check.setOnClickListener(v -> listener.onToggle(entry));
            binding.row.setOnClickListener(v -> {
                if (selectionMode) listener.onToggle(entry);
                else listener.onOpen(entry);
            });
            binding.row.setOnLongClickListener(v -> {
                if (selectionMode) listener.onToggle(entry);
                else listener.onLongPress(entry);
                return true;
            });

            String fileName = Storage.displayName(context, entry.localUri);
            binding.title.setText(MediaItem.notEmpty(fileName) ? fileName : entry.title);
            StringBuilder meta = new StringBuilder();
            if (entry.author != null && !entry.author.isEmpty()) meta.append("@").append(entry.author);
            if (entry.story) {
                if (meta.length() > 0) meta.append("  •  ");
                meta.append(context.getString(R.string.story_label));
            }
            if (entry.slideshow) {
                if (meta.length() > 0) meta.append("  •  ");
                meta.append(context.getString(R.string.slideshow_label));
            }
            if (entry.createdAt > 0) {
                if (meta.length() > 0) meta.append("  •  ");
                meta.append(Ui.formatDate(entry.createdAt));
            }
            binding.meta.setText(meta);

            binding.thumb.setImageTintList(null);
            binding.thumb.clearColorFilter();
            ImageLoader.load(binding.thumb, entry.thumbUrl, R.drawable.ic_video);
            binding.captionBadge.setVisibility(Settings.CAPTION_EMBED.equals(entry.captionMode)
                    ? View.VISIBLE : View.GONE);
            binding.storyBadge.setVisibility(entry.story ? View.VISIBLE : View.GONE);
            String quality = qualityLabel(context, entry);
            binding.qualityBadge.setText(quality);
            binding.qualityBadge.setVisibility(quality.isEmpty() ? View.GONE : View.VISIBLE);
            binding.slideshowBadge.setVisibility(entry.slideshow ? View.VISIBLE : View.GONE);
            binding.formatBadges.setVisibility(quality.isEmpty() && !entry.slideshow
                    ? View.GONE : View.VISIBLE);

            if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
                binding.typeIcon.setImageResource(R.drawable.ic_music);
                binding.typeIcon.setColorFilter(ThemeManager.attrColor(context, R.attr.colorTertiary));
            } else if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
                binding.typeIcon.setImageResource(R.drawable.ic_image);
                binding.typeIcon.setColorFilter(context.getColor(R.color.warning));
            } else {
                binding.typeIcon.setImageResource(R.drawable.ic_video);
                binding.typeIcon.setColorFilter(ThemeManager.attrColor(context, R.attr.colorPrimary));
            }

            boolean running = entry.isRunning();
            boolean paused = entry.isPaused();
            boolean failed = DownloadEntry.STATUS_FAILED.equals(entry.status);
            boolean complete = entry.isComplete();
            binding.progress.setVisibility(running || paused ? View.VISIBLE : View.GONE);
            binding.btnPause.setVisibility(running || paused ? View.VISIBLE : View.GONE);
            binding.btnCancel.setVisibility(running || paused ? View.VISIBLE : View.GONE);
            binding.btnRetry.setVisibility(failed ? View.VISIBLE : View.GONE);
            binding.btnMore.setVisibility(selectionMode || running || paused ? View.GONE : View.VISIBLE);
            if (selectionMode) {
                binding.btnPause.setVisibility(View.GONE);
                binding.btnCancel.setVisibility(View.GONE);
                binding.btnRetry.setVisibility(View.GONE);
                binding.progress.setVisibility(View.GONE);
            }
            binding.btnPause.setIconResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
            binding.btnPause.setContentDescription(context.getString(paused
                    ? R.string.resume_download : R.string.pause_download));

            if (running || paused) {
                binding.progress.setProgressCompat(
                        DownloadEntry.STATUS_QUEUED.equals(entry.status) ? 0 : entry.progressPercent(),
                        true);
                String status;
                if (DownloadEntry.STATUS_QUEUED.equals(entry.status)) {
                    status = context.getString(R.string.queued);
                } else if (entry.size > 0) {
                    status = entry.progressPercent() + "%  •  "
                            + Ui.formatBytes(entry.bytes) + " / " + Ui.formatBytes(entry.size);
                } else {
                    status = context.getString(R.string.downloading) + "  •  "
                            + Ui.formatBytes(entry.bytes);
                }
                if (paused) status = context.getString(R.string.status_paused) + "  •  " + status;
                binding.status.setText(status);
            } else if (complete) {
                String size = entry.size > 0 ? "  •  " + Ui.formatBytes(entry.size) : "";
                binding.status.setText(context.getString(R.string.status_completed) + size);
            } else if (failed) {
                binding.status.setText(R.string.status_failed);
            } else {
                binding.status.setText(R.string.status_cancelled);
            }

            binding.thumb.setOnClickListener(v -> {
                if (selectionMode) listener.onToggle(entry);
                else listener.onOpen(entry);
            });
            binding.thumbBox.setOnClickListener(v -> {
                if (selectionMode) listener.onToggle(entry);
                else listener.onOpen(entry);
            });
            binding.thumb.setOnLongClickListener(v -> {
                if (selectionMode) listener.onToggle(entry);
                else listener.onLongPress(entry);
                return true;
            });
            binding.thumbBox.setOnLongClickListener(v -> {
                if (selectionMode) listener.onToggle(entry);
                else listener.onLongPress(entry);
                return true;
            });
            binding.btnPause.setOnClickListener(v -> listener.onPause(entry));
            binding.btnRetry.setOnClickListener(v -> listener.onRetry(entry));
            binding.btnCancel.setOnClickListener(v -> listener.onCancel(entry));
            binding.btnMore.setOnClickListener(v -> listener.onMenu(v, entry));
        }
    }
}
