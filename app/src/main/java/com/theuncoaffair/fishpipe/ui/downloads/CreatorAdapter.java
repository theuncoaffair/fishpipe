package com.theuncoaffair.fishpipe.ui.downloads;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.CreatorInfo;
import com.theuncoaffair.fishpipe.databinding.ItemCreatorListBinding;
import com.theuncoaffair.fishpipe.util.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CreatorAdapter extends RecyclerView.Adapter<CreatorAdapter.Holder> {
    public interface Listener {
        void onOpen(CreatorInfo creator);

        void onLongPress(CreatorInfo creator);

        void onToggle(CreatorInfo creator);

        void onMenu(View anchor, CreatorInfo creator);
    }

    private final List<CreatorInfo> items = new ArrayList<>();
    private final Listener listener;
    private Set<String> selected = Collections.emptySet();
    private boolean selectionMode;

    public CreatorAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<CreatorInfo> next) {
        List<CreatorInfo> incoming = next == null ? Collections.emptyList() : new ArrayList<>(next);
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
                return Objects.equals(items.get(oldPosition).name, incoming.get(newPosition).name);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                CreatorInfo a = items.get(oldPosition);
                CreatorInfo b = incoming.get(newPosition);
                return a.count == b.count && Objects.equals(a.avatar, b.avatar);
            }
        });
        items.clear();
        items.addAll(incoming);
        result.dispatchUpdatesTo(this);
    }

    public void setSelection(boolean mode, Set<String> selectedNames) {
        selectionMode = mode;
        selected = selectedNames == null ? Collections.emptySet() : selectedNames;
        notifyItemRangeChanged(0, items.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemCreatorListBinding.inflate(
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

    final class Holder extends RecyclerView.ViewHolder {
        private final ItemCreatorListBinding binding;

        Holder(ItemCreatorListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CreatorInfo creator) {
            Context context = binding.getRoot().getContext();
            binding.creatorName.setText(context.getString(R.string.by_author, creator.name));
            binding.creatorCount.setText(
                    context.getString(R.string.creator_video_count, creator.count));
            ImageLoader.load(binding.creatorAvatar, creator.avatar, R.drawable.ic_app);
            binding.check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            binding.more.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
            binding.check.setChecked(selected.contains(creator.name));
            binding.check.setOnClickListener(v -> listener.onToggle(creator));
            binding.more.setOnClickListener(v -> listener.onMenu(v, creator));
            binding.row.setOnClickListener(v -> {
                if (selectionMode) listener.onToggle(creator);
                else listener.onOpen(creator);
            });
            binding.row.setOnLongClickListener(v -> {
                if (selectionMode) listener.onToggle(creator);
                else listener.onLongPress(creator);
                return true;
            });
        }
    }
}
