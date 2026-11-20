package com.theuncoaffair.fishpipe.ui.creatorvideos;

import androidx.annotation.NonNull;

import com.theuncoaffair.fishpipe.data.DownloadEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CreatorVideosUiState {
    @NonNull public final String author;
    @NonNull public final String avatar;
    @NonNull public final List<DownloadEntry> items;
    @NonNull public final Set<Long> selected;
    public final boolean selectionMode;
    public final boolean loading;
    public final boolean deleting;
    public final int actionNonce;
    public final int deletedCount;

    public CreatorVideosUiState(@NonNull String author, @NonNull String avatar,
                                @NonNull List<DownloadEntry> items, @NonNull Set<Long> selected,
                                boolean selectionMode, boolean loading, boolean deleting,
                                int actionNonce, int deletedCount) {
        this.author = author;
        this.avatar = avatar;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.selected = Collections.unmodifiableSet(new LinkedHashSet<>(selected));
        this.selectionMode = selectionMode;
        this.loading = loading;
        this.deleting = deleting;
        this.actionNonce = actionNonce;
        this.deletedCount = deletedCount;
    }

    public boolean isSelected(long rowId) {
        return selected.contains(rowId);
    }

    public int selectedCount() {
        return selected.size();
    }

    public boolean allSelected() {
        return !items.isEmpty() && selected.size() == items.size();
    }

    @NonNull
    public List<DownloadEntry> selectedItems() {
        List<DownloadEntry> out = new ArrayList<>();
        for (DownloadEntry entry : items) {
            if (selected.contains(entry.rowId)) out.add(entry);
        }
        return out;
    }
}
