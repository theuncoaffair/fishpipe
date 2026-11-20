package com.theuncoaffair.fishpipe.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ItemCreatorVideoBinding;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class CreatorVideoAdapter
        extends ListAdapter<DownloadEntry, CreatorVideoAdapter.Holder> {
    public interface Listener {
        void onOpen(DownloadEntry entry);

        void onToggle(DownloadEntry entry);

        void onBeginSelection(DownloadEntry entry);

        void onMore(View anchor, DownloadEntry entry);
    }

    private static final DiffUtil.ItemCallback<DownloadEntry> DIFF =
            new DiffUtil.ItemCallback<DownloadEntry>() {
                @Override
                public boolean areItemsTheSame(@NonNull DownloadEntry oldItem,
                                               @NonNull DownloadEntry newItem) {
                    return oldItem.rowId == newItem.rowId;
                }

                @Override
                public boolean areContentsTheSame(@NonNull DownloadEntry oldItem,
                                                  @NonNull DownloadEntry newItem) {
                    return oldItem.rowId == newItem.rowId
                            && Objects.equals(oldItem.localUri, newItem.localUri)
                            && Objects.equals(oldItem.thumbUrl, newItem.thumbUrl)
                            && Objects.equals(oldItem.title, newItem.title)
                            && Objects.equals(oldItem.type, newItem.type)
                            && Objects.equals(oldItem.captionMode, newItem.captionMode)
                            && oldItem.uploadedAt == newItem.uploadedAt
                            && Objects.equals(oldItem.status, newItem.status)
                            && oldItem.size == newItem.size
                            && oldItem.bytes == newItem.bytes;
                }
            };

    private final Listener listener;
    private Set<Long> selected = Collections.emptySet();
    private boolean selectionMode;

    public CreatorVideoAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setSelection(Set<Long> selected, boolean selectionMode) {
        Set<Long> next = new LinkedHashSet<>(selected);
        if (this.selectionMode == selectionMode && this.selected.equals(next)) return;
        this.selected = next;
        this.selectionMode = selectionMode;
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).rowId;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCreatorVideoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DownloadEntry entry = getItem(position);
        ItemCreatorVideoBinding binding = holder.binding;
        boolean chosen = selected.contains(entry.rowId);
        binding.checkWrap.setBackgroundResource(chosen
                ? R.drawable.bg_check_checked : R.drawable.bg_check_unchecked);
        binding.check.setVisibility(chosen ? View.VISIBLE : View.GONE);
        binding.checkWrap.setVisibility(selectionMode ? View.VISIBLE : View.GONE);

        String date = Ui.formatUploadTime(binding.getRoot().getContext(), entry.uploadedAt);
        binding.label.setText(date);
        binding.label.setVisibility(date.isEmpty() ? View.GONE : View.VISIBLE);
        binding.captionBadge.setVisibility(Settings.CAPTION_EMBED.equals(entry.captionMode)
                ? View.VISIBLE : View.GONE);
        binding.typeBadge.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
            binding.typeBadge.setImageResource(R.drawable.ic_music);
            binding.typeBadge.setContentDescription(
                    binding.getRoot().getContext().getString(R.string.type_audio));
        } else if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
            binding.typeBadge.setImageResource(R.drawable.ic_image);
            binding.typeBadge.setContentDescription(
                    binding.getRoot().getContext().getString(R.string.type_photo));
        } else {
            binding.typeBadge.setImageResource(R.drawable.ic_video);
            binding.typeBadge.setContentDescription(
                    binding.getRoot().getContext().getString(R.string.type_video));
        }
        binding.more.setVisibility(View.VISIBLE);
        binding.thumb.setImageTintList(null);
        ImageLoader.load(binding.thumb, entry.thumbUrl, R.drawable.ic_video);

        binding.getRoot().setOnClickListener(v -> {
            if (selectionMode) listener.onToggle(entry);
            else listener.onOpen(entry);
        });
        binding.getRoot().setOnLongClickListener(v -> {
            if (selectionMode) listener.onToggle(entry);
            else listener.onBeginSelection(entry);
            return true;
        });
        binding.checkWrap.setOnClickListener(v -> listener.onToggle(entry));
        binding.more.setOnClickListener(v -> listener.onMore(v, entry));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemCreatorVideoBinding binding;

        Holder(ItemCreatorVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
