package com.theuncoaffair.fishpipe.ui.language;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.databinding.ItemLanguageBinding;

import java.util.ArrayList;
import java.util.List;

public final class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.Holder> {
    public interface Listener {
        void onSelected(LanguageOption option);
    }

    private final List<LanguageOption> items = new ArrayList<>();
    private final Listener listener;

    public LanguageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<LanguageOption> next) {
        List<LanguageOption> incoming = next == null ? new ArrayList<>() : new ArrayList<>(next);
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
                return items.get(oldPosition).code.equals(incoming.get(newPosition).code);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return items.get(oldPosition).equals(incoming.get(newPosition));
            }
        });
        items.clear();
        items.addAll(incoming);
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemLanguageBinding.inflate(
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
        private final ItemLanguageBinding binding;

        Holder(ItemLanguageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LanguageOption option) {
            binding.label.setText(option.label);
            binding.check.setChecked(option.selected);
            binding.row.setOnClickListener(v -> listener.onSelected(option));
        }
    }
}
