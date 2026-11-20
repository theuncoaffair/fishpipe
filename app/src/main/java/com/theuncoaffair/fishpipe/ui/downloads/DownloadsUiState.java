package com.theuncoaffair.fishpipe.ui.downloads;

import androidx.annotation.NonNull;

import com.theuncoaffair.fishpipe.data.CreatorInfo;
import com.theuncoaffair.fishpipe.data.DownloadEntry;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DownloadsUiState {
    public static final String FILTER_CREATORS = "creators";

    public enum Action {
        NONE,
        DELETED,
        HISTORY_CLEARED,
        NO_SELECTION,
        PAUSED,
        RESUMED,
        RETRYING
    }

    @NonNull public final String filter;
    @NonNull public final List<DownloadEntry> downloads;
    @NonNull public final List<CreatorInfo> creators;
    public final boolean loading;
    public final boolean selectionMode;
    @NonNull public final Set<Long> selectedDownloads;
    @NonNull public final Set<String> selectedCreators;
    @NonNull public final Action action;
    public final int actionNonce;
    public final int actionCount;

    public DownloadsUiState(@NonNull String filter, @NonNull List<DownloadEntry> downloads,
                            @NonNull List<CreatorInfo> creators, boolean loading,
                            boolean selectionMode, @NonNull Set<Long> selectedDownloads,
                            @NonNull Set<String> selectedCreators, @NonNull Action action,
                            int actionNonce, int actionCount) {
        this.filter = filter;
        this.downloads = Collections.unmodifiableList(downloads);
        this.creators = Collections.unmodifiableList(creators);
        this.loading = loading;
        this.selectionMode = selectionMode;
        this.selectedDownloads = Collections.unmodifiableSet(selectedDownloads);
        this.selectedCreators = Collections.unmodifiableSet(selectedCreators);
        this.action = action;
        this.actionNonce = actionNonce;
        this.actionCount = actionCount;
    }

    public static DownloadsUiState empty() {
        return new DownloadsUiState(FILTER_CREATORS, Collections.emptyList(),
                Collections.emptyList(), true, false, Collections.emptySet(),
                Collections.emptySet(), Action.NONE, 0, 0);
    }

    public boolean isCreators() {
        return FILTER_CREATORS.equals(filter);
    }

    public int itemCount() {
        return isCreators() ? creators.size() : downloads.size();
    }

    public int selectedCount() {
        return isCreators() ? selectedCreators.size() : selectedDownloads.size();
    }

    public boolean allSelected() {
        return itemCount() > 0 && selectedCount() == itemCount();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DownloadsUiState)) return false;
        DownloadsUiState that = (DownloadsUiState) other;
        return loading == that.loading
                && selectionMode == that.selectionMode
                && actionNonce == that.actionNonce
                && actionCount == that.actionCount
                && filter.equals(that.filter)
                && downloads.equals(that.downloads)
                && creators.equals(that.creators)
                && selectedDownloads.equals(that.selectedDownloads)
                && selectedCreators.equals(that.selectedCreators)
                && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, downloads, creators, loading, selectionMode, selectedDownloads,
                selectedCreators, action, actionNonce, actionCount);
    }
}
