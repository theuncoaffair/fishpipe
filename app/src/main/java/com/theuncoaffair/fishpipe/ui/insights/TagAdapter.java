package com.theuncoaffair.fishpipe.ui.insights;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.analytics.InsightsResult;
import com.theuncoaffair.fishpipe.databinding.ItemInsightTagBinding;

import java.util.Locale;
import java.util.Objects;

public final class TagAdapter extends ListAdapter<InsightsResult.TagStat, TagAdapter.Holder> {
    public interface Listener {
        void onTag(InsightsResult.TagStat tag);
    }

    private static final DiffUtil.ItemCallback<InsightsResult.TagStat> DIFF =
            new DiffUtil.ItemCallback<InsightsResult.TagStat>() {
                @Override
                public boolean areItemsTheSame(@NonNull InsightsResult.TagStat oldItem,
                                               @NonNull InsightsResult.TagStat newItem) {
                    return Objects.equals(oldItem.tag, newItem.tag);
                }

                @Override
                public boolean areContentsTheSame(@NonNull InsightsResult.TagStat oldItem,
                                                  @NonNull InsightsResult.TagStat newItem) {
                    return Objects.equals(oldItem.tag, newItem.tag)
                            && oldItem.uses == newItem.uses
                            && oldItem.views == newItem.views;
                }
            };

    private final Listener listener;

    public TagAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemInsightTagBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InsightsResult.TagStat tag = getItem(position);
        holder.binding.tagName.setText(holder.binding.getRoot().getContext().getString(
                R.string.insights_tag_name, tag.tag));
        holder.binding.tagMeta.setText(holder.binding.getRoot().getContext().getString(
                R.string.insights_tag_meta, InsightFormat.compact(tag.uses), InsightFormat.compact(tag.views)));
        holder.binding.getRoot().setOnClickListener(v -> listener.onTag(tag));
    }


    static final class Holder extends RecyclerView.ViewHolder {
        final ItemInsightTagBinding binding;

        Holder(ItemInsightTagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
