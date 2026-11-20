package com.theuncoaffair.fishpipe.ui.batchdownload;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.net.TikWmProfileResolver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class BatchDownloadViewModel extends ViewModel {
    private final Context context;
    private final ExecutorService io;
    private final ResolveRepository resolver;
    private final MutableLiveData<BatchDownloadUiState> state =
            new MutableLiveData<>(BatchDownloadUiState.empty());

    private String author = "";
    private String avatar = "";
    private int actionNonce;
    private int startedCount;
    private int openNonce;
    private int optionsNonce;
    private int messageNonce;

    @Inject
    public BatchDownloadViewModel(@ApplicationContext Context context,
                                  @Named(AppModule.IO) ExecutorService io,
                                  ResolveRepository resolver) {
        this.context = context;
        this.io = io;
        this.resolver = resolver;
    }

    public LiveData<BatchDownloadUiState> getState() {
        return state;
    }

    public void load(@Nullable String rawAuthor, @Nullable String rawAvatar) {
        author = clean(rawAuthor);
        avatar = rawAvatar == null ? "" : rawAvatar;
        startedCount = 0;
        if (author.isEmpty()) {
            state.setValue(new BatchDownloadUiState(author, avatar, new ArrayList<>(),
                    new LinkedHashSet<>(), false, false, false,
                    context.getString(R.string.batch_link_missing), actionNonce, 0, null,
                    openNonce, null, new ArrayList<>(), optionsNonce, 0, messageNonce));
            return;
        }
        List<MediaItem> loaded = new ArrayList<>();
        state.setValue(new BatchDownloadUiState(author, avatar, loaded, new LinkedHashSet<>(),
                false, true, false, "", actionNonce, 0, null, openNonce, null,
                new ArrayList<>(), optionsNonce, 0, messageNonce));
        final String handle = author;
        io.execute(() -> {
            try {
                List<MediaItem> items = new TikWmProfileResolver().fetch(handle, page -> {
                    if (page == null || page.isEmpty()) return;
                    List<MediaItem> merged = merge(loaded, page);
                    loaded.clear();
                    loaded.addAll(merged);
                    updateAvatar(merged);
                    BatchDownloadUiState current = state.getValue();
                    Set<String> selected = retain(current == null ? new LinkedHashSet<>()
                            : current.selectedKeys, merged);
                    boolean mode = current != null && current.selectionMode && !selected.isEmpty();
                    state.postValue(withItems(merged, selected, mode, true, false, ""));
                });
                List<MediaItem> merged = merge(loaded, items);
                loaded.clear();
                loaded.addAll(merged);
                updateAvatar(merged);
                if (merged.isEmpty()) {
                    state.postValue(withItems(new ArrayList<>(), new LinkedHashSet<>(),
                            false, false, false, context.getString(R.string.batch_empty)));
                    return;
                }
                BatchDownloadUiState current = state.getValue();
                Set<String> selected = retain(current == null ? new LinkedHashSet<>()
                        : current.selectedKeys, merged);
                boolean mode = current != null && current.selectionMode && !selected.isEmpty();
                state.postValue(withItems(merged, selected, mode, false, false, ""));
            } catch (Exception ignored) {
                List<MediaItem> currentItems = new ArrayList<>(loaded);
                if (currentItems.isEmpty()) {
                    state.postValue(withItems(new ArrayList<>(), new LinkedHashSet<>(),
                            false, false, false, context.getString(R.string.batch_empty)));
                    return;
                }
                BatchDownloadUiState current = state.getValue();
                Set<String> selected = retain(current == null ? new LinkedHashSet<>()
                        : current.selectedKeys, currentItems);
                boolean mode = current != null && current.selectionMode && !selected.isEmpty();
                state.postValue(withItems(currentItems, selected, mode, false, false, ""));
            }
        });
    }

    public void retry() {
        load(author, avatar);
    }

    public void toggle(@NonNull MediaItem item) {
        BatchDownloadUiState current = state.getValue();
        if (current == null) return;
        Set<String> selected = new LinkedHashSet<>(current.selectedKeys);
        String key = BatchDownloadUiState.key(item);
        boolean mode = current.selectionMode;
        if (!mode) {
            mode = true;
            selected.add(key);
        } else if (!selected.remove(key)) {
            selected.add(key);
        }
        state.setValue(withItems(current.items, selected, mode,
                current.loading, current.batchRunning, current.errorMessage));
    }

    public void beginSelection(@NonNull MediaItem item) {
        BatchDownloadUiState current = state.getValue();
        if (current == null || current.selectionMode) {
            toggle(item);
            return;
        }
        Set<String> selected = new LinkedHashSet<>();
        selected.add(BatchDownloadUiState.key(item));
        state.setValue(withItems(current.items, selected, true,
                current.loading, current.batchRunning, current.errorMessage));
    }

    public void selectAll() {
        BatchDownloadUiState current = state.getValue();
        if (current == null || current.items.isEmpty()) return;
        Set<String> selected = new LinkedHashSet<>();
        for (MediaItem item : current.items) selected.add(BatchDownloadUiState.key(item));
        state.setValue(withItems(current.items, selected, true,
                current.loading, current.batchRunning, current.errorMessage));
    }

    public void clearSelection() {
        BatchDownloadUiState current = state.getValue();
        if (current == null) return;
        state.setValue(withItems(current.items, new LinkedHashSet<>(), false,
                current.loading, current.batchRunning, current.errorMessage));
    }

    public void remove(@NonNull MediaItem item) {
        BatchDownloadUiState current = state.getValue();
        if (current == null) return;
        String key = BatchDownloadUiState.key(item);
        List<MediaItem> items = new ArrayList<>();
        for (MediaItem existing : current.items) {
            if (!key.equals(BatchDownloadUiState.key(existing))) items.add(existing);
        }
        Set<String> selected = new LinkedHashSet<>(current.selectedKeys);
        selected.remove(key);
        boolean mode = current.selectionMode && !items.isEmpty();
        state.setValue(withItems(items, selected, mode,
                current.loading, current.batchRunning, current.errorMessage));
    }

    public void requestOpen(@NonNull MediaItem item) {
        if (item.hasImages() || item.hasVideo()) {
            publishOpen(item);
            return;
        }
        if (!MediaItem.notEmpty(item.pageUrl)) {
            message(R.string.no_media);
            return;
        }
        message(R.string.resolving_link);
        io.execute(() -> {
            MediaItem resolved = null;
            try {
                resolved = resolver.resolve(item.pageUrl);
            } catch (Exception ignored) {
            }
            MediaItem target = resolved;
            if (target == null || (!target.hasImages() && !target.hasVideo())) {
                message(R.string.resolve_failed);
                return;
            }
            publishOpen(target);
        });
    }

    public void prepareOptions() {
        BatchDownloadUiState current = state.getValue();
        if (current == null || current.loading || current.batchRunning) return;
        final List<MediaItem> selected = current.selectedItems();
        if (selected.isEmpty()) {
            message(R.string.no_items_selected);
            return;
        }
        final MediaItem sample = selected.get(0);
        if (!needsResolution(sample)) {
            publishOptions(sample, selected, false);
            return;
        }
        state.setValue(withItems(current.items, current.selectedKeys, current.selectionMode,
                current.loading, true, current.errorMessage));
        io.execute(() -> {
            MediaItem resolved = null;
            if (MediaItem.notEmpty(sample.pageUrl)) {
                try {
                    resolved = resolver.resolve(sample.pageUrl);
                } catch (Exception ignored) {
                }
            }
            MediaItem optionItem = resolved != null && resolved.hasVideo() ? resolved : sample;
            List<MediaItem> selectedNow = new ArrayList<>(selected);
            if (optionItem != sample) {
                String sampleKey = BatchDownloadUiState.key(sample);
                for (int i = 0; i < selectedNow.size(); i++) {
                    if (sampleKey.equals(BatchDownloadUiState.key(selectedNow.get(i)))) {
                        selectedNow.set(i, optionItem);
                        break;
                    }
                }
            }
            publishOptions(optionItem, selectedNow, false);
        });
    }

    public void enqueueSelected(@NonNull List<MediaItem> selected, @NonNull String kind,
                                boolean preferHd, boolean captions, @NonNull String captionMode) {
        BatchDownloadUiState current = state.getValue();
        if (current == null || current.batchRunning || selected.isEmpty()) return;
        state.setValue(withItems(current.items, current.selectedKeys, current.selectionMode,
                current.loading, true, current.errorMessage));
        io.execute(() -> {
            int added = 0;
            for (MediaItem item : selected) {
                MediaItem target = item;
                if (!target.hasVideo()
                        || (DownloadController.KIND_MUSIC.equals(kind) && !target.hasMusic())
                        || (DownloadController.KIND_COVER.equals(kind) && !target.hasCover())) {
                    if (MediaItem.notEmpty(target.pageUrl)) {
                        try {
                            target = resolver.resolve(target.pageUrl);
                        } catch (Exception ignored) {
                        }
                    }
                }
                int count = DownloadController.enqueue(context, target, kind, preferHd,
                        captions, captionMode);
                if (count == 0 && DownloadController.KIND_HD.equals(kind)) {
                    count = DownloadController.enqueue(context, target, DownloadController.KIND_VIDEO,
                            true, captions, captionMode);
                }
                added += count;
            }
            startedCount = added;
            actionNonce++;
            BatchDownloadUiState latest = state.getValue();
            if (latest == null) return;
            state.postValue(withItems(latest.items, latest.selectedKeys, latest.selectionMode,
                    latest.loading, false, latest.errorMessage));
        });
    }

    private void publishOpen(@NonNull MediaItem item) {
        BatchDownloadUiState current = state.getValue();
        if (current == null) return;
        openNonce++;
        state.postValue(new BatchDownloadUiState(current.author, current.avatar, current.items,
                current.selectedKeys, current.selectionMode, current.loading, current.batchRunning,
                current.errorMessage, actionNonce, startedCount, item, openNonce,
                current.optionsSample, current.optionsSelected, current.optionsNonce,
                current.messageRes, current.messageNonce));
    }

    private void publishOptions(@NonNull MediaItem sample, @NonNull List<MediaItem> selected,
                                boolean batchRunning) {
        BatchDownloadUiState current = state.getValue();
        if (current == null) return;
        optionsNonce++;
        state.postValue(new BatchDownloadUiState(current.author, current.avatar, current.items,
                current.selectedKeys, current.selectionMode, current.loading, batchRunning,
                current.errorMessage, actionNonce, startedCount, current.openItem, current.openNonce,
                sample, selected, optionsNonce, current.messageRes, current.messageNonce));
    }

    private void message(@StringRes int res) {
        BatchDownloadUiState current = state.getValue();
        if (current == null) return;
        messageNonce++;
        state.postValue(new BatchDownloadUiState(current.author, current.avatar, current.items,
                current.selectedKeys, current.selectionMode, current.loading, current.batchRunning,
                current.errorMessage, actionNonce, startedCount, current.openItem, current.openNonce,
                current.optionsSample, current.optionsSelected, current.optionsNonce,
                res, messageNonce));
    }

    private BatchDownloadUiState withItems(@NonNull List<MediaItem> items,
                                           @NonNull Set<String> selected,
                                           boolean selectionMode,
                                           boolean loading,
                                           boolean batchRunning,
                                           @NonNull String errorMessage) {
        BatchDownloadUiState current = state.getValue();
        return new BatchDownloadUiState(author, avatar, items, selected, selectionMode, loading,
                batchRunning, errorMessage, actionNonce, startedCount,
                current == null ? null : current.openItem,
                current == null ? 0 : current.openNonce,
                current == null ? null : current.optionsSample,
                current == null ? new ArrayList<>() : current.optionsSelected,
                current == null ? 0 : current.optionsNonce,
                current == null ? 0 : current.messageRes,
                current == null ? 0 : current.messageNonce);
    }

    private void updateAvatar(@NonNull List<MediaItem> items) {
        if (!avatar.isEmpty()) return;
        for (MediaItem item : items) {
            if (MediaItem.notEmpty(item.authorAvatar)) {
                avatar = item.authorAvatar;
                return;
            }
        }
    }

    @NonNull
    private static List<MediaItem> merge(@NonNull List<MediaItem> existing,
                                         @Nullable List<MediaItem> page) {
        List<MediaItem> out = new ArrayList<>(existing);
        if (page == null || page.isEmpty()) return out;
        Set<String> known = new LinkedHashSet<>();
        for (MediaItem item : out) known.add(BatchDownloadUiState.key(item));
        for (MediaItem item : page) {
            if (item == null) continue;
            if (known.add(BatchDownloadUiState.key(item))) out.add(item);
        }
        return out;
    }

    @NonNull
    private static Set<String> retain(@NonNull Set<String> selected,
                                      @NonNull List<MediaItem> items) {
        Set<String> known = new LinkedHashSet<>();
        for (MediaItem item : items) known.add(BatchDownloadUiState.key(item));
        Set<String> out = new LinkedHashSet<>();
        for (String key : selected) {
            if (known.contains(key)) out.add(key);
        }
        return out;
    }

    private static boolean needsResolution(@NonNull MediaItem item) {
        return !item.hasVideo() || !item.hasMusic() || !item.hasCover()
                || !MediaItem.notEmpty(item.videoHdUrl);
    }

    @NonNull
    private static String clean(@Nullable String value) {
        String handle = value == null ? "" : value.trim();
        while (handle.startsWith("@")) handle = handle.substring(1);
        int cut = handle.indexOf('?');
        if (cut >= 0) handle = handle.substring(0, cut);
        cut = handle.indexOf('/');
        if (cut >= 0) handle = handle.substring(0, cut);
        return handle;
    }
}
