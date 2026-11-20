package com.theuncoaffair.fishpipe.ui.settingsdetail;

import androidx.annotation.NonNull;

public final class SettingsDetailUiState {
    @NonNull public final String versionName;
    @NonNull public final String updateMessage;
    public final boolean checking;
    @NonNull public final String downloadUrl;
    public final int resultNonce;

    public SettingsDetailUiState(@NonNull String versionName, @NonNull String updateMessage,
                                 boolean checking, @NonNull String downloadUrl, int resultNonce) {
        this.versionName = versionName;
        this.updateMessage = updateMessage;
        this.checking = checking;
        this.downloadUrl = downloadUrl;
        this.resultNonce = resultNonce;
    }
}
