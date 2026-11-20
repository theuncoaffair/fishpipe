package com.theuncoaffair.fishpipe.ui.downloads;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.CreatorInfo;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.repository.DownloadRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class DownloadsViewModel extends ViewModel {
    private final DownloadRepository downloads;
    private final ExecutorService io;
    private final MutableLiveData<DownloadsUiState> state = new MutableLiveData<>();
    private final AtomicInteger loadId = new AtomicInteger();
    private final AtomicInteger actionNonce = new AtomicInteger();
    private String filter = DownloadsUiState.FILTER_CREATORS;

    @Inject
    public DownloadsViewModel(DownloadRepository downloads, @Named("io") ExecutorService io) {
        this.downloads = downloads;
        this.io = io;
        state.setValue(DownloadsUiState.empty());
        reload();
    }

    public LiveData<DownloadsUiState> getState() {
        return state;
    }

    public void reload() {
        reloadInternal(DownloadsUiState.Action.NONE, 0);
    }

    public void setFilter(@NonNull String nextFilter) {
        if (nextFilter.equals(filter)) {
            reload();
            return;
        }
        filter = nextFilter;
        DownloadsUiState current = current();
        update(new DownloadsUiState(filter, current.downloads, current.creators, true, false,
                new LinkedHashSet<>(), new LinkedHashSet<>(), DownloadsUiState.Action.NONE,
                actionNonce.get(), 0));
        reloadInternal(DownloadsUiState.Action.NONE, 0);
    }

    public void beginSelection(DownloadEntry entry) {
        DownloadsUiState current = current();
        Set<Long> selected = new LinkedHashSet<>();
        selected.add(entry.rowId);
        update(new DownloadsUiState(current.filter, current.downloads, current.creators,
                current.loading, true, selected, new LinkedHashSet<>(),
                DownloadsUiState.Action.NONE, actionNonce.get(), 0));
    }

    public void toggleDownload(DownloadEntry entry) {
        DownloadsUiState current = current();
        if (!current.selectionMode) {
            beginSelection(entry);
            return;
        }
        Set<Long> selected = new LinkedHashSet<>(current.selectedDownloads);
        if (!selected.remove(entry.rowId)) selected.add(entry.rowId);
        boolean mode = !selected.isEmpty();
        update(new DownloadsUiState(current.filter, current.downloads, current.creators,
                current.loading, mode, selected, new LinkedHashSet<>(),
                DownloadsUiState.Action.NONE, actionNonce.get(), 0));
    }

    public void beginSelection(CreatorInfo creator) {
        DownloadsUiState current = current();
        Set<String> selected = new LinkedHashSet<>();
        selected.add(creator.name);
        update(new DownloadsUiState(current.filter, current.downloads, current.creators,
                current.loading, true, new LinkedHashSet<>(), selected,
                DownloadsUiState.Action.NONE, actionNonce.get(), 0));
    }

    public void toggleCreator(CreatorInfo creator) {
        DownloadsUiState current = current();
        if (!current.selectionMode) {
            beginSelection(creator);
            return;
        }
        Set<String> selected = new LinkedHashSet<>(current.selectedCreators);
        if (!selected.remove(creator.name)) selected.add(creator.name);
        boolean mode = !selected.isEmpty();
        update(new DownloadsUiState(current.filter, current.downloads, current.creators,
                current.loading, mode, new LinkedHashSet<>(), selected,
                DownloadsUiState.Action.NONE, actionNonce.get(), 0));
    }

    public void selectAll() {
        DownloadsUiState current = current();
        Set<Long> selectedDownloads = new LinkedHashSet<>();
        Set<String> selectedCreators = new LinkedHashSet<>();
        if (current.isCreators()) {
            for (CreatorInfo creator : current.creators) selectedCreators.add(creator.name);
        } else {
            for (DownloadEntry entry : current.downloads) selectedDownloads.add(entry.rowId);
        }
        boolean mode = !(selectedDownloads.isEmpty() && selectedCreators.isEmpty());
        update(new DownloadsUiState(current.filter, current.downloads, current.creators,
                current.loading, mode, selectedDownloads, selectedCreators,
                DownloadsUiState.Action.NONE, actionNonce.get(), 0));
    }

    public void clearSelection() {
        DownloadsUiState current = current();
        update(new DownloadsUiState(current.filter, current.downloads, current.creators,
                current.loading, false, new LinkedHashSet<>(), new LinkedHashSet<>(),
                DownloadsUiState.Action.NONE, actionNonce.get(), 0));
    }

    public void cancel(DownloadEntry entry) {
        downloads.cancel(entry.rowId);
        reloadInternal(DownloadsUiState.Action.NONE, 0);
    }

    public void pauseOrResume(DownloadEntry entry) {
        if (entry.isPaused()) {
            downloads.resume(entry.rowId);
            reloadInternal(DownloadsUiState.Action.RESUMED, 0);
        } else {
            downloads.pause(entry.rowId);
            reloadInternal(DownloadsUiState.Action.PAUSED, 0);
        }
    }

    public void retry(DownloadEntry entry) {
        downloads.retry(entry.rowId);
        reloadInternal(DownloadsUiState.Action.RETRYING, 0);
    }

    public void deleteSelected(boolean deleteFiles) {
        DownloadsUiState current = current();
        if (current.selectedCount() == 0) {
            update(new DownloadsUiState(current.filter, current.downloads, current.creators,
                    current.loading, current.selectionMode, current.selectedDownloads,
                    current.selectedCreators, DownloadsUiState.Action.NO_SELECTION,
                    actionNonce.incrementAndGet(), 0));
            return;
        }
        final String currentFilter = current.filter;
        final List<DownloadEntry> selectedEntries = new ArrayList<>();
        for (DownloadEntry entry : current.downloads) {
            if (current.selectedDownloads.contains(entry.rowId)) selectedEntries.add(entry);
        }
        final List<CreatorInfo> selectedCreators = new ArrayList<>();
        for (CreatorInfo creator : current.creators) {
            if (current.selectedCreators.contains(creator.name)) selectedCreators.add(creator);
        }
        final boolean creators = current.isCreators();
        io.execute(() -> {
            int count = 0;
            if (creators) {
                for (CreatorInfo creator : selectedCreators) {
                    count += downloads.listByAuthor(creator.name).size();
                    downloads.deleteByAuthor(creator.name, deleteFiles);
                }
            } else {
                count = selectedEntries.size();
                downloads.deleteEntries(selectedEntries, deleteFiles);
            }
            filter = currentFilter;
            reloadInternal(DownloadsUiState.Action.DELETED, count);
        });
    }

    public void deleteCreator(@NonNull CreatorInfo creator, boolean deleteFiles) {
        io.execute(() -> {
            int count = downloads.listByAuthor(creator.name).size();
            downloads.deleteByAuthor(creator.name, deleteFiles);
            reloadInternal(DownloadsUiState.Action.DELETED, count);
        });
    }

    public void clearTabHistory(boolean deleteFiles) {
        final String currentFilter = filter;
        io.execute(() -> {
            if (DownloadsUiState.FILTER_CREATORS.equals(currentFilter)) {
                if (deleteFiles) {
                    for (CreatorInfo creator : downloads.listCreators()) {
                        downloads.deleteByAuthor(creator.name, true);
                    }
                }
                downloads.clearCreators();
            } else {
                downloads.clearHistoryByType(currentFilter, deleteFiles);
            }
            reloadInternal(DownloadsUiState.Action.HISTORY_CLEARED, 0);
        });
    }

    public void clearHistory(boolean deleteFiles) {
        io.execute(() -> {
            downloads.clearHistory(deleteFiles);
            reloadInternal(DownloadsUiState.Action.HISTORY_CLEARED, 0);
        });
    }

    private void reloadInternal(DownloadsUiState.Action action, int actionCount) {
        final int id = loadId.incrementAndGet();
        final DownloadsUiState current = current();
        final int nonce = action == DownloadsUiState.Action.NONE
                ? actionNonce.get() : actionNonce.incrementAndGet();
        postState(new DownloadsUiState(filter, current.downloads, current.creators, true,
                current.selectionMode, current.selectedDownloads, current.selectedCreators,
                action, nonce, actionCount));
        io.execute(() -> {
            List<DownloadEntry> entries = new ArrayList<>();
            List<CreatorInfo> creators = new ArrayList<>();
            if (DownloadsUiState.FILTER_CREATORS.equals(filter)) {
                creators.addAll(downloads.listCreators());
            } else {
                entries.addAll(downloads.listByType(filter, false));
            }
            if (id != loadId.get()) return;
            Set<Long> selectedDownloads = new LinkedHashSet<>();
            for (DownloadEntry entry : entries) {
                if (current.selectedDownloads.contains(entry.rowId)) {
                    selectedDownloads.add(entry.rowId);
                }
            }
            Set<String> selectedCreators = new LinkedHashSet<>();
            for (CreatorInfo creator : creators) {
                if (current.selectedCreators.contains(creator.name)) {
                    selectedCreators.add(creator.name);
                }
            }
            boolean selectionMode = current.selectionMode
                    && !(selectedDownloads.isEmpty() && selectedCreators.isEmpty());
            postState(new DownloadsUiState(filter, entries, creators, false, selectionMode,
                    selectedDownloads, selectedCreators, action, nonce, actionCount));
        });
    }

    private DownloadsUiState current() {
        DownloadsUiState current = state.getValue();
        return current == null ? DownloadsUiState.empty() : current;
    }

    private void update(DownloadsUiState next) {
        if (!next.equals(state.getValue())) {
            state.setValue(next);
        }
    }

    private void postState(DownloadsUiState next) {
        state.postValue(next);
    }
}
