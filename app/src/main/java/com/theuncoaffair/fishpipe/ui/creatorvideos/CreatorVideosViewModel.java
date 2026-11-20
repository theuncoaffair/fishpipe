package com.theuncoaffair.fishpipe.ui.creatorvideos;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.repository.DownloadRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class CreatorVideosViewModel extends ViewModel {
    private final DownloadRepository repository;
    private final ExecutorService io;
    private final MutableLiveData<CreatorVideosUiState> state = new MutableLiveData<>();
    private String author = "";
    private String avatar = "";
    private int actionNonce;
    private int deletedCount;

    @Inject
    public CreatorVideosViewModel(DownloadRepository repository,
                                  @Named("io") ExecutorService io) {
        this.repository = repository;
        this.io = io;
        state.setValue(new CreatorVideosUiState("", "", new ArrayList<>(), new LinkedHashSet<>(),
                false, false, false, 0, 0));
    }

    public LiveData<CreatorVideosUiState> getState() {
        return state;
    }

    public void load(String rawAuthor, String rawAvatar) {
        author = clean(rawAuthor);
        avatar = rawAvatar == null ? "" : rawAvatar;
        if (author.isEmpty()) {
            state.setValue(new CreatorVideosUiState(author, avatar, new ArrayList<>(),
                    new LinkedHashSet<>(), false, false, false, actionNonce, deletedCount));
            return;
        }
        state.setValue(new CreatorVideosUiState(author, avatar, new ArrayList<>(),
                new LinkedHashSet<>(), false, true, false, actionNonce, deletedCount));
        loadItems(false);
    }

    public void refresh() {
        if (author.isEmpty()) return;
        loadItems(true);
    }

    public void toggle(DownloadEntry entry) {
        CreatorVideosUiState current = state.getValue();
        if (current == null || entry == null) return;
        Set<Long> selected = new LinkedHashSet<>(current.selected);
        boolean selectionMode = current.selectionMode;
        if (!selectionMode) {
            selectionMode = true;
            selected.add(entry.rowId);
        } else if (selected.contains(entry.rowId)) {
            selected.remove(entry.rowId);
        } else {
            selected.add(entry.rowId);
        }
        publish(current.items, selected, selectionMode, current.loading, current.deleting);
    }

    public void selectAll() {
        CreatorVideosUiState current = state.getValue();
        if (current == null) return;
        Set<Long> selected = new LinkedHashSet<>();
        for (DownloadEntry entry : current.items) selected.add(entry.rowId);
        publish(current.items, selected, !selected.isEmpty(), current.loading, current.deleting);
    }

    public void clearSelection() {
        CreatorVideosUiState current = state.getValue();
        if (current == null) return;
        publish(current.items, new LinkedHashSet<>(), false, current.loading, current.deleting);
    }

    public void deleteSelected() {
        CreatorVideosUiState current = state.getValue();
        if (current == null || current.deleting) return;
        final List<DownloadEntry> selected = current.selectedItems();
        if (selected.isEmpty()) return;
        publish(current.items, current.selected, current.selectionMode, current.loading, true);
        io.execute(() -> {
            repository.deleteEntries(selected, true);
            List<DownloadEntry> items = repository.listByAuthor(author);
            deletedCount = selected.size();
            actionNonce++;
            state.postValue(new CreatorVideosUiState(author, avatar, items,
                    new LinkedHashSet<>(), false, false, false, actionNonce, deletedCount));
        });
    }

    private void loadItems(boolean preserveSelection) {
        CreatorVideosUiState current = state.getValue();
        final boolean selectionMode = preserveSelection && current != null && current.selectionMode;
        final Set<Long> selected = preserveSelection && current != null
                ? new LinkedHashSet<>(current.selected) : new LinkedHashSet<>();
        final boolean deleting = current != null && current.deleting;
        io.execute(() -> {
            List<DownloadEntry> items = repository.listByAuthor(author);
            Set<Long> kept = new LinkedHashSet<>();
            if (selectionMode) {
                for (DownloadEntry entry : items) {
                    if (selected.contains(entry.rowId)) kept.add(entry.rowId);
                }
            }
            state.postValue(new CreatorVideosUiState(author, avatar, items, kept,
                    selectionMode && !kept.isEmpty(), false, deleting, actionNonce, deletedCount));
        });
    }

    private void publish(List<DownloadEntry> items, Set<Long> selected, boolean selectionMode,
                         boolean loading, boolean deleting) {
        state.setValue(new CreatorVideosUiState(author, avatar, items, selected,
                selectionMode, loading, deleting, actionNonce, deletedCount));
    }

    private static String clean(String value) {
        String handle = value == null ? "" : value.trim();
        while (handle.startsWith("@")) handle = handle.substring(1);
        int cut = handle.indexOf('?');
        if (cut >= 0) handle = handle.substring(0, cut);
        cut = handle.indexOf('/');
        if (cut >= 0) handle = handle.substring(0, cut);
        return handle;
    }
}
