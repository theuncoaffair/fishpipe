package com.theuncoaffair.fishpipe.ui.insights;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.analytics.InsightsResult;
import com.theuncoaffair.fishpipe.databinding.ItemInsightSampleBinding;
import com.theuncoaffair.fishpipe.util.ImageLoader;

import java.util.Locale;
import java.util.Objects;

public final class SampleAdapter
        extends ListAdapter<InsightsResult.SampleVideo, SampleAdapter.Holder> {
    public interface Listener {
        void onSample(InsightsResult.SampleVideo sample);
    }

    private static final DiffUtil.ItemCallback<InsightsResult.SampleVideo> DIFF =
            new DiffUtil.ItemCallback<InsightsResult.SampleVideo>() {
                @Override
                public boolean areItemsTheSame(@NonNull InsightsResult.SampleVideo oldItem,
                                               @NonNull InsightsResult.SampleVideo newItem) {
                    return Objects.equals(oldItem.id, newItem.id)
                            && Objects.equals(oldItem.pageUrl, newItem.pageUrl);
                }

                @Override
                public boolean areContentsTheSame(@NonNull InsightsResult.SampleVideo oldItem,
                                                  @NonNull InsightsResult.SampleVideo newItem) {
                    return Objects.equals(oldItem.id, newItem.id)
                            && Objects.equals(oldItem.pageUrl, newItem.pageUrl)
                            && Objects.equals(oldItem.description, newItem.description)
                            && Objects.equals(oldItem.author, newItem.author)
                            && Objects.equals(oldItem.cover, newItem.cover)
                            && oldItem.views == newItem.views;
                }
            };

    private final Listener listener;

    public SampleAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemInsightSampleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InsightsResult.SampleVideo sample = getItem(position);
        ItemInsightSampleBinding binding = holder.binding;
        ImageLoader.load(binding.sampleCover, sample.cover, R.drawable.ic_image);
        binding.sampleTitle.setText(sample.description == null
                || sample.description.trim().isEmpty() ? sample.id : sample.description);
        String author = sample.author == null || sample.author.trim().isEmpty()
                ? "" : binding.getRoot().getContext().getString(
                R.string.by_author, sample.author);
        binding.sampleAuthor.setText(author);
        binding.sampleAuthor.setVisibility(TextUtils.isEmpty(author) ? View.GONE : View.VISIBLE);
        binding.sampleViews.setText(binding.getRoot().getContext().getString(
                R.string.plays, InsightFormat.compact(sample.views)));
        binding.getRoot().setOnClickListener(v -> listener.onSample(sample));
    }


    static final class Holder extends RecyclerView.ViewHolder {
        final ItemInsightSampleBinding binding;

        Holder(ItemInsightSampleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
