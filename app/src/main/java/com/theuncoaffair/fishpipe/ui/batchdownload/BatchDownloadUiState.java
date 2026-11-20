package com.theuncoaffair.fishpipe.ui.batchdownload;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.theuncoaffair.fishpipe.data.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BatchDownloadUiState {
    @NonNull
    public final String author;
    @NonNull
    public final String avatar;
    @NonNull
    public final List<MediaItem> items;
    @NonNull
    public final Set<String> selectedKeys;
    public final boolean selectionMode;
    public final boolean loading;
    public final boolean batchRunning;
    @NonNull
    public final String errorMessage;
    public final int actionNonce;
    public final int startedCount;
    @Nullable
    public final MediaItem openItem;
    public final int openNonce;
    @Nullable
    public final MediaItem optionsSample;
    @NonNull
    public final List<MediaItem> optionsSelected;
    public final int optionsNonce;
    @StringRes
    public final int messageRes;
    public final int messageNonce;

    public BatchDownloadUiState(@NonNull String author,
                                @NonNull String avatar,
                                @NonNull List<MediaItem> items,
                                @NonNull Set<String> selectedKeys,
                                boolean selectionMode,
                                boolean loading,
                                boolean batchRunning,
                                @NonNull String errorMessage,
                                int actionNonce,
                                int startedCount,
                                @Nullable MediaItem openItem,
                                int openNonce,
                                @Nullable MediaItem optionsSample,
                                @NonNull List<MediaItem> optionsSelected,
                                int optionsNonce,
                                @StringRes int messageRes,
                                int messageNonce) {
        this.author = author;
        this.avatar = avatar;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.selectedKeys = Collections.unmodifiableSet(new LinkedHashSet<>(selectedKeys));
        this.selectionMode = selectionMode;
        this.loading = loading;
        this.batchRunning = batchRunning;
        this.errorMessage = errorMessage;
        this.actionNonce = actionNonce;
        this.startedCount = startedCount;
        this.openItem = openItem;
        this.openNonce = openNonce;
        this.optionsSample = optionsSample;
        this.optionsSelected = Collections.unmodifiableList(new ArrayList<>(optionsSelected));
        this.optionsNonce = optionsNonce;
        this.messageRes = messageRes;
        this.messageNonce = messageNonce;
    }

    @NonNull
    public static BatchDownloadUiState empty() {
        return new BatchDownloadUiState("", "", new ArrayList<>(), new LinkedHashSet<>(),
                false, false, false, "", 0, 0, null, 0, null, new ArrayList<>(), 0, 0, 0);
    }

    public int selectedCount() {
        return selectedKeys.size();
    }

    public boolean allSelected() {
        return !items.isEmpty() && selectedKeys.size() == items.size();
    }

    public boolean isSelected(@NonNull MediaItem item) {
        return selectedKeys.contains(key(item));
    }

    @NonNull
    public List<MediaItem> selectedItems() {
        List<MediaItem> out = new ArrayList<>();
        for (MediaItem item : items) {
            if (selectedKeys.contains(key(item))) out.add(item);
        }
        return out;
    }

    @NonNull
    public static String key(@NonNull MediaItem item) {
        if (MediaItem.notEmpty(item.id)) return item.id;
        if (MediaItem.notEmpty(item.pageUrl)) return item.pageUrl;
        String video = item.bestVideo(false);
        return video == null ? "" : video;
    }
}
