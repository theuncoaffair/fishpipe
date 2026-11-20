package com.theuncoaffair.fishpipe.ui.insights;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.analytics.WatchlistDb;
import com.theuncoaffair.fishpipe.databinding.ItemInsightWatchBinding;
import com.theuncoaffair.fishpipe.util.ImageLoader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class WatchlistAdapter
        extends ListAdapter<WatchlistDb.Entry, WatchlistAdapter.Holder> {
    public interface Listener {
        void onClick(WatchlistDb.Entry entry);

        void onLongClick(WatchlistDb.Entry entry);

        void onMore(View anchor, WatchlistDb.Entry entry);
    }

    private static final DiffUtil.ItemCallback<WatchlistDb.Entry> DIFF =
            new DiffUtil.ItemCallback<WatchlistDb.Entry>() {
                @Override
                public boolean areItemsTheSame(@NonNull WatchlistDb.Entry oldItem,
                                               @NonNull WatchlistDb.Entry newItem) {
                    return Objects.equals(oldItem.uniqueId, newItem.uniqueId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull WatchlistDb.Entry oldItem,
                                                  @NonNull WatchlistDb.Entry newItem) {
                    return Objects.equals(oldItem.uniqueId, newItem.uniqueId)
                            && Objects.equals(oldItem.nickname, newItem.nickname)
                            && Objects.equals(oldItem.avatar, newItem.avatar)
                            && oldItem.followers == newItem.followers
                            && oldItem.videos == newItem.videos;
                }
            };

    private final Listener listener;
    private Set<String> selected = Collections.emptySet();
    private boolean selecting;

    public WatchlistAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setSelection(Set<String> selected, boolean selecting) {
        this.selected = selected == null
                ? Collections.emptySet() : new HashSet<>(selected);
        this.selecting = selecting;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemInsightWatchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        WatchlistDb.Entry entry = getItem(position);
        ItemInsightWatchBinding binding = holder.binding;
        binding.watchCheck.setVisibility(selecting ? View.VISIBLE : View.GONE);
        binding.watchCheck.setChecked(selected.contains(entry.uniqueId));
        ImageLoader.load(binding.watchAvatar, entry.avatar, R.drawable.ic_explore);
        binding.watchName.setText(entry.nickname == null || entry.nickname.trim().isEmpty()
                ? binding.getRoot().getContext().getString(R.string.by_author, entry.uniqueId)
                : entry.nickname);
        if (entry.followers > 0) {
            binding.watchMeta.setText(binding.getRoot().getContext().getString(
                    R.string.insights_watch_meta,
                    InsightFormat.compact(entry.followers), InsightFormat.compact(entry.videos)));
        } else {
            binding.watchMeta.setText(binding.getRoot().getContext().getString(
                    R.string.insights_watch_meta_videos, InsightFormat.compact(entry.videos)));
        }
        binding.watchMore.setOnClickListener(v -> listener.onMore(v, entry));
        binding.getRoot().setOnClickListener(v -> listener.onClick(entry));
        binding.getRoot().setOnLongClickListener(v -> {
            listener.onLongClick(entry);
            return true;
        });
    }


    static final class Holder extends RecyclerView.ViewHolder {
        final ItemInsightWatchBinding binding;

        Holder(ItemInsightWatchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
