package com.theuncoaffair.fishpipe.ui.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.theuncoaffair.fishpipe.data.MediaItem;

import java.util.Objects;

public final class HomeUiState {
    public enum Error {
        NONE,
        INVALID_LINK,
        NETWORK,
        STORY_EXPIRED,
        RESOLVE_FAILED
    }

    public enum Action {
        NONE,
        DOWNLOAD_STARTED,
        NO_MEDIA
    }

    @NonNull public final String link;
    public final boolean resolving;
    @Nullable public final MediaItem item;
    public final boolean clipboardVisible;
    @NonNull public final String clipboardUrl;
    public final boolean pro;
    public final int remaining;
    public final boolean autoDownloadPending;
    @NonNull public final Error error;
    @NonNull public final Action action;
    public final int actionNonce;

    public HomeUiState(@NonNull String link, boolean resolving, @Nullable MediaItem item,
                       boolean clipboardVisible, @NonNull String clipboardUrl, boolean pro,
                       int remaining, boolean autoDownloadPending, @NonNull Error error,
                       @NonNull Action action, int actionNonce) {
        this.link = link;
        this.resolving = resolving;
        this.item = item;
        this.clipboardVisible = clipboardVisible;
        this.clipboardUrl = clipboardUrl;
        this.pro = pro;
        this.remaining = remaining;
        this.autoDownloadPending = autoDownloadPending;
        this.error = error;
        this.action = action;
        this.actionNonce = actionNonce;
    }

    public static HomeUiState empty(boolean pro, int remaining) {
        return new HomeUiState("", false, null, false, "", pro, remaining, false,
                Error.NONE, Action.NONE, 0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HomeUiState)) return false;
        HomeUiState that = (HomeUiState) other;
        return resolving == that.resolving
                && clipboardVisible == that.clipboardVisible
                && pro == that.pro
                && remaining == that.remaining
                && autoDownloadPending == that.autoDownloadPending
                && actionNonce == that.actionNonce
                && link.equals(that.link)
                && Objects.equals(item, that.item)
                && clipboardUrl.equals(that.clipboardUrl)
                && error == that.error
                && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(link, resolving, item, clipboardVisible, clipboardUrl, pro, remaining,
                autoDownloadPending, error, action, actionNonce);
    }
}
