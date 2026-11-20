package com.theuncoaffair.fishpipe.region;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.databinding.ItemRegionBinding;

import java.util.ArrayList;
import java.util.List;

public final class RegionAdapter extends RecyclerView.Adapter<RegionAdapter.Holder> {
    public interface Listener {
        void onSelected(Region region);
    }

    private final List<Region> items = new ArrayList<>();
    private final Listener listener;
    private String selectedCode = "";

    public RegionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Region> next, String selected) {
        List<Region> incoming = next == null ? new ArrayList<>() : new ArrayList<>(next);
        final String oldSelected = selectedCode;
        final String newSelected = selected == null ? "" : selected;
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
                return items.get(oldPosition).code.equalsIgnoreCase(incoming.get(newPosition).code);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Region a = items.get(oldPosition);
                Region b = incoming.get(newPosition);
                boolean wasSelected = a.code.equalsIgnoreCase(oldSelected);
                boolean isSelected = b.code.equalsIgnoreCase(newSelected);
                return a.name.equals(b.name) && wasSelected == isSelected;
            }
        });
        items.clear();
        items.addAll(incoming);
        selectedCode = newSelected;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemRegionBinding.inflate(
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
        private final ItemRegionBinding binding;

        Holder(ItemRegionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Region region) {
            binding.regionCode.setText(region.code);
            binding.regionName.setText(region.name);
            binding.regionCheck.setChecked(region.code.equalsIgnoreCase(selectedCode));
            binding.row.setOnClickListener(v -> listener.onSelected(region));
        }
    }
}
