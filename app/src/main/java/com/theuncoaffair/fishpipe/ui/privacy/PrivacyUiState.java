package com.theuncoaffair.fishpipe.ui.privacy;

public final class PrivacyUiState {
    public final boolean clearing;
    public final int clearedNonce;

    public PrivacyUiState(boolean clearing, int clearedNonce) {
        this.clearing = clearing;
        this.clearedNonce = clearedNonce;
    }
}
