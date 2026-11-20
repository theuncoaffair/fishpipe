package com.theuncoaffair.fishpipe.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.databinding.ItemBatchVideoBinding;
import com.theuncoaffair.fishpipe.ui.batchdownload.BatchDownloadUiState;
import com.theuncoaffair.fishpipe.util.ImageLoader;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class BatchVideoAdapter
        extends ListAdapter<MediaItem, BatchVideoAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull MediaItem item);

        void onToggle(@NonNull MediaItem item);

        void onBeginSelection(@NonNull MediaItem item);

        void onMore(@NonNull View anchor, @NonNull MediaItem item);
    }

    private static final DiffUtil.ItemCallback<MediaItem> DIFF =
            new DiffUtil.ItemCallback<MediaItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull MediaItem oldItem,
                                               @NonNull MediaItem newItem) {
                    return BatchDownloadUiState.key(oldItem)
                            .equals(BatchDownloadUiState.key(newItem));
                }

                @Override
                public boolean areContentsTheSame(@NonNull MediaItem oldItem,
                                                  @NonNull MediaItem newItem) {
                    return BatchDownloadUiState.key(oldItem)
                            .equals(BatchDownloadUiState.key(newItem))
                            && equal(oldItem.coverUrl, newItem.coverUrl)
                            && equal(oldItem.title, newItem.title)
                            && equal(oldItem.source, newItem.source)
                            && oldItem.diggCount == newItem.diggCount
                            && oldItem.hasImages() == newItem.hasImages();
                }

                private boolean equal(String left, String right) {
                    return left == null ? right == null : left.equals(right);
                }
            };

    private final Context context;
    private final Listener listener;
    private Set<String> selected = new LinkedHashSet<>();
    private boolean selectionMode;

    public BatchVideoAdapter(@NonNull Context context, @NonNull Listener listener) {
        super(DIFF);
        this.context = context;
        this.listener = listener;
    }

    public void setSelection(@NonNull Set<String> keys, boolean selecting) {
        selected = new LinkedHashSet<>(keys);
        selectionMode = selecting;
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBatchVideoBinding binding = ItemBatchVideoBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaItem item = getItem(position);
        boolean chosen = selected.contains(BatchDownloadUiState.key(item));
        holder.binding.checkWrap.setBackgroundResource(chosen
                ? R.drawable.bg_check_checked : R.drawable.bg_check_unchecked);
        holder.binding.check.setVisibility(chosen ? View.VISIBLE : View.GONE);
        holder.binding.checkWrap.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.binding.type.setImageResource(item.hasImages()
                ? R.drawable.ic_image : R.drawable.ic_video);
        holder.binding.type.setContentDescription(context.getString(
                item.hasImages() ? R.string.type_image : R.string.type_video));
        boolean tiktokLikes = "tiktok".equalsIgnoreCase(item.source);
        holder.binding.likesRow.setVisibility(tiktokLikes ? View.VISIBLE : View.GONE);
        holder.binding.likes.setText(compact(item.diggCount));
        holder.binding.more.setVisibility(View.VISIBLE);
        holder.binding.thumb.setImageTintList(null);
        ImageLoader.load(holder.binding.thumb, item.coverUrl, R.drawable.ic_video);
        holder.binding.getRoot().setAlpha(1f);
        holder.binding.getRoot().setOnClickListener(v -> {
            if (selectionMode) listener.onToggle(item);
            else listener.onOpen(item);
        });
        holder.binding.getRoot().setOnLongClickListener(v -> {
            if (selectionMode) listener.onToggle(item);
            else listener.onBeginSelection(item);
            return true;
        });
        holder.binding.checkWrap.setOnClickListener(v -> listener.onToggle(item));
        holder.binding.more.setOnClickListener(v -> listener.onMore(v, item));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemBatchVideoBinding binding;

        Holder(@NonNull ItemBatchVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static String compact(long value) {
        if (value >= 1_000_000_000L) return trim(value / 1_000_000_000d) + "B";
        if (value >= 1_000_000L) return trim(value / 1_000_000d) + "M";
        if (value >= 1_000L) return trim(value / 1_000d) + "K";
        return String.valueOf(value);
    }

    private static String trim(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
