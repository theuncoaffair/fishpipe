package com.theuncoaffair.fishpipe.ui.home;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.repository.DownloadRepository;
import com.theuncoaffair.fishpipe.data.repository.LicenseRepository;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;
import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;
import com.theuncoaffair.fishpipe.download.ClipboardWatchService;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.util.Notifications;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class HomeViewModel extends ViewModel {
    private final ResolveRepository resolver;
    private final DownloadRepository downloads;
    private final LicenseRepository license;
    private final SettingsRepository settings;
    private final Context context;
    private final ExecutorService io;
    private final MutableLiveData<HomeUiState> state = new MutableLiveData<>();
    private int requestId;
    private int actionNonce;

    @Inject
    public HomeViewModel(ResolveRepository resolver, DownloadRepository downloads,
                         LicenseRepository license, SettingsRepository settings,
                         @ApplicationContext Context context, @Named("io") ExecutorService io) {
        this.resolver = resolver;
        this.downloads = downloads;
        this.license = license;
        this.settings = settings;
        this.context = context.getApplicationContext();
        this.io = io;
        state.setValue(HomeUiState.empty(license.isProActive(), license.remaining()));
    }

    public LiveData<HomeUiState> getState() {
        return state;
    }

    public void refreshLicense() {
        HomeUiState current = current();
        update(new HomeUiState(current.link, current.resolving, current.item,
                current.clipboardVisible, current.clipboardUrl, license.isProActive(),
                license.remaining(), current.autoDownloadPending, current.error, current.action,
                current.actionNonce));
    }

    public void resetUsage() {
        license.resetUsage();
        refreshLicense();
    }

    public void setLink(@NonNull String link) {
        HomeUiState current = current();
        update(new HomeUiState(link, current.resolving, current.item, current.clipboardVisible,
                current.clipboardUrl, current.pro, current.remaining,
                current.autoDownloadPending, HomeUiState.Error.NONE, current.action,
                current.actionNonce));
    }

    public void onClipboardText(@Nullable String text) {
        HomeUiState current = current();
        if (!settings.isAutoDetect()) {
            updateClipboard(current, false, "");
            return;
        }
        String url = text == null ? "" : resolver.findUrl(text);
        if (url.isEmpty() || url.equals(settings.getLastClipboard())) {
            updateClipboard(current, false, "");
            return;
        }
        updateClipboard(current, true, url);
    }

    public void dismissClipboard() {
        HomeUiState current = current();
        updateClipboard(current, false, "");
    }

    public void useClipboardLink() {
        HomeUiState current = current();
        if (current.clipboardUrl.isEmpty()) return;
        settings.setLastClipboard(current.clipboardUrl);
        Notifications.cancelClipboard(context);
        updateClipboard(current, false, "");
        resolve(current.clipboardUrl, false);
    }

    public void resolveClipboard(@Nullable String clipboardText) {
        String url = clipboardText == null ? "" : resolver.findUrl(clipboardText);
        if (url.isEmpty()) {
            showError(HomeUiState.Error.INVALID_LINK);
            return;
        }
        resolve(url, false);
    }

    public void clear() {
        requestId++;
        HomeUiState current = current();
        update(new HomeUiState("", false, null, false, "", current.pro, current.remaining,
                false, HomeUiState.Error.NONE, HomeUiState.Action.NONE, current.actionNonce));
    }

    public void resolve(@NonNull String raw, boolean autoDownload) {
        String query = raw.trim();
        if (query.isEmpty()) {
            showError(HomeUiState.Error.INVALID_LINK);
            return;
        }
        String found = resolver.findUrl(query);
        if (!found.isEmpty()) {
            settings.setLastClipboard(found);
        }
        String normalized = resolver.normalize(query);
        boolean numeric = query.matches("\\d{15,}");
        if (normalized.isEmpty() && !numeric) {
            showError(HomeUiState.Error.INVALID_LINK);
            return;
        }
        if (!numeric && !resolver.isSupported(normalized)
                && settings.getCustomResolver().isEmpty()) {
            showError(HomeUiState.Error.INVALID_LINK);
            return;
        }

        HomeUiState current = current();
        update(new HomeUiState(query, true, null, false, "", current.pro, current.remaining,
                autoDownload, HomeUiState.Error.NONE, HomeUiState.Action.NONE,
                current.actionNonce));
        final int id = ++requestId;
        io.execute(() -> {
            try {
                MediaItem item = resolver.resolve(query);
                if (id != requestId) return;
                postState(new HomeUiState(query, false, item, false, "", license.isProActive(),
                        license.remaining(), autoDownload, HomeUiState.Error.NONE,
                        HomeUiState.Action.NONE, actionNonce));
            } catch (Exception error) {
                if (id != requestId) return;
                postState(new HomeUiState(query, false, null, false, "", license.isProActive(),
                        license.remaining(), false, errorFor(error, query),
                        HomeUiState.Action.NONE, actionNonce));
            }
        });
    }

    public void clearAutoDownloadPending() {
        HomeUiState current = current();
        if (!current.autoDownloadPending) return;
        update(new HomeUiState(current.link, current.resolving, current.item,
                current.clipboardVisible, current.clipboardUrl, current.pro, current.remaining,
                false, current.error, current.action, current.actionNonce));
    }

    public void autoDownloadNow() {
        MediaItem item = current().item;
        if (item == null) return;
        int count;
        if (item.hasVideo()) {
            count = downloads.enqueueDefaultVideo(item);
        } else if (item.hasImages()) {
            count = downloads.enqueueDefaultPhotos(item);
        } else if (item.hasMusic()) {
            count = downloads.enqueueDefaultAudio(item);
        } else {
            count = 0;
        }
        reportQueue(count);
    }

    public void enqueue(String kind, boolean preferHd, boolean captions, String captionMode) {
        MediaItem item = current().item;
        if (item == null) return;
        reportQueue(downloads.enqueue(item, kind, preferHd, captions, captionMode));
    }

    public void enqueueImages(List<String> images, boolean withMusic) {
        MediaItem item = current().item;
        if (item == null) return;
        reportQueue(downloads.enqueueImages(item, images, withMusic));
    }

    public void enqueueSlideshow(List<String> images) {
        MediaItem item = current().item;
        if (item == null) return;
        reportQueue(downloads.enqueueSlideshow(item));
    }

    public void consumeError() {
        HomeUiState current = current();
        if (current.error == HomeUiState.Error.NONE) return;
        update(new HomeUiState(current.link, current.resolving, current.item,
                current.clipboardVisible, current.clipboardUrl, current.pro, current.remaining,
                current.autoDownloadPending, HomeUiState.Error.NONE, current.action,
                current.actionNonce));
    }

    private void reportQueue(int count) {
        HomeUiState current = current();
        actionNonce++;
        update(new HomeUiState(current.link, current.resolving, current.item,
                current.clipboardVisible, current.clipboardUrl, current.pro, current.remaining,
                false, current.error,
                count > 0 ? HomeUiState.Action.DOWNLOAD_STARTED : HomeUiState.Action.NO_MEDIA,
                actionNonce));
        if (count > 0) {
            ClipboardWatchService.sync(context);
        }
    }

    private void showError(HomeUiState.Error error) {
        HomeUiState current = current();
        update(new HomeUiState(current.link, false, current.item, current.clipboardVisible,
                current.clipboardUrl, current.pro, current.remaining, false, error,
                current.action, current.actionNonce));
    }

    private void updateClipboard(HomeUiState current, boolean visible, String url) {
        update(new HomeUiState(current.link, current.resolving, current.item, visible, url,
                current.pro, current.remaining, current.autoDownloadPending, current.error,
                current.action, current.actionNonce));
    }

    private HomeUiState current() {
        HomeUiState current = state.getValue();
        return current == null
                ? HomeUiState.empty(license.isProActive(), license.remaining()) : current;
    }

    private void update(HomeUiState next) {
        if (!next.equals(state.getValue())) {
            state.setValue(next);
        }
    }

    private void postState(HomeUiState next) {
        HomeUiState current = state.getValue();
        if (current == null || next.actionNonce >= current.actionNonce) {
            state.postValue(next);
        }
    }

    private HomeUiState.Error errorFor(Exception error, String query) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        if (cause instanceof UnknownHostException || cause instanceof ConnectException
                || cause instanceof SocketTimeoutException) {
            return HomeUiState.Error.NETWORK;
        }
        if (LinkUtils.isStory(query) || LinkUtils.isStory(resolver.findUrl(query))) {
            return HomeUiState.Error.STORY_EXPIRED;
        }
        return HomeUiState.Error.RESOLVE_FAILED;
    }
}
