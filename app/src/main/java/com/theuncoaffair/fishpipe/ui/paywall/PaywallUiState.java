package com.theuncoaffair.fishpipe.ui.paywall;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public final class PaywallUiState {
    public final boolean configured;
    public final boolean busy;
    public final boolean developer;
    public final boolean devDismiss;
    @NonNull
    public final String initialEmail;
    @NonNull
    public final String usesText;
    @NonNull
    public final String deviceText;
    @NonNull
    public final String statusMessage;
    @StringRes
    public final int emailError;
    @StringRes
    public final int codeError;
    public final int emailErrorNonce;
    public final int codeErrorNonce;
    public final int sentNonce;
    @StringRes
    public final int toastRes;
    public final int toastNonce;
    public final int finishNonce;

    public PaywallUiState(boolean configured,
                          boolean busy,
                          boolean developer,
                          boolean devDismiss,
                          @NonNull String initialEmail,
                          @NonNull String usesText,
                          @NonNull String deviceText,
                          @NonNull String statusMessage,
                          @StringRes int emailError,
                          @StringRes int codeError,
                          int emailErrorNonce,
                          int codeErrorNonce,
                          int sentNonce,
                          @StringRes int toastRes,
                          int toastNonce,
                          int finishNonce) {
        this.configured = configured;
        this.busy = busy;
        this.developer = developer;
        this.devDismiss = devDismiss;
        this.initialEmail = initialEmail;
        this.usesText = usesText;
        this.deviceText = deviceText;
        this.statusMessage = statusMessage;
        this.emailError = emailError;
        this.codeError = codeError;
        this.emailErrorNonce = emailErrorNonce;
        this.codeErrorNonce = codeErrorNonce;
        this.sentNonce = sentNonce;
        this.toastRes = toastRes;
        this.toastNonce = toastNonce;
        this.finishNonce = finishNonce;
    }
}
