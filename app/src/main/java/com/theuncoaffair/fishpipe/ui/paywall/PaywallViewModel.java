package com.theuncoaffair.fishpipe.ui.paywall;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.billing.LicenseClient;
import com.theuncoaffair.fishpipe.data.repository.LicenseRepository;
import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.pricing.AnnualPrice;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class PaywallViewModel extends ViewModel {
    private final Context context;
    private final SettingsRepository settings;
    private final LicenseRepository license;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<PaywallUiState> state = new MutableLiveData<>();

    private final boolean configured;
    private final boolean developer;
    private final String initialEmail;
    private boolean devDismiss;
    private boolean busy;
    private String status;
    private int emailError;
    private int codeError;
    private int emailErrorNonce;
    private int codeErrorNonce;
    private int sentNonce;
    private int toastRes;
    private int toastNonce;
    private int finishNonce;

    @Inject
    public PaywallViewModel(@ApplicationContext Context context,
                            SettingsRepository settings,
                            LicenseRepository license,
                            @Named(AppModule.IO) ExecutorService io) {
        this.context = context;
        this.settings = settings;
        this.license = license;
        this.io = io;
        configured = LicenseClient.isConfigured();
        developer = license.isDeveloper();
        initialEmail = settings.getProEmail();
        devDismiss = settings.isPaywallDismissed();
        status = configured ? "" : context.getString(R.string.paywall_not_configured);
        publish();
    }

    public LiveData<PaywallUiState> getState() {
        return state;
    }

    public String annualPrice() {
        return AnnualPrice.forRegion(settings.getRegionCode()).display();
    }

    public String paymentUrl() {
        return AnnualPrice.forRegion(settings.getRegionCode()).checkoutUrl();
    }

    public void sendCode(@Nullable String rawEmail) {
        if (busy || !configured) return;
        final String email = rawEmail == null ? "" : rawEmail.trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = R.string.paywall_email_invalid;
            emailErrorNonce++;
            publish();
            return;
        }
        emailError = 0;
        codeError = 0;
        busy = true;
        status = context.getString(R.string.paywall_sending);
        publish();
        final String hash = license.deviceHash();
        io.execute(() -> {
            LicenseClient.Result result = LicenseClient.requestCode(email, hash);
            main.post(() -> {
                busy = false;
                if (result.ok) {
                    status = context.getString(R.string.paywall_sent);
                    sentNonce++;
                } else {
                    status = result.message;
                }
                publish();
            });
        });
    }

    public void verify(@Nullable String rawEmail, @Nullable String rawCode) {
        if (busy || !configured) return;
        final String email = rawEmail == null ? "" : rawEmail.trim();
        final String code = rawCode == null ? "" : rawCode.trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = R.string.paywall_email_invalid;
            emailErrorNonce++;
            publish();
            return;
        }
        if (code.length() != 6) {
            codeError = R.string.paywall_code_invalid;
            codeErrorNonce++;
            publish();
            return;
        }
        emailError = 0;
        codeError = 0;
        busy = true;
        status = context.getString(R.string.paywall_verifying);
        publish();
        final String hash = license.deviceHash();
        io.execute(() -> {
            LicenseClient.Result result = LicenseClient.verifyCode(email, code, hash);
            main.post(() -> {
                busy = false;
                handleActivation(email, result);
            });
        });
    }

    public void setDismiss(boolean dismissed) {
        devDismiss = dismissed;
        settings.setPaywallDismissed(dismissed);
        publish();
    }

    private void handleActivation(@NonNull String buyerEmail, @NonNull LicenseClient.Result result) {
        if (!result.ok) {
            status = result.message;
            publish();
            return;
        }
        switch (result.status) {
            case "activated":
            case "already_active":
                String activatedEmail = result.email.isEmpty() ? buyerEmail : result.email;
                settings.activatePro(activatedEmail, result.sessionToken,
                        result.expiresAt, result.licenseExpiresAt);
                status = "";
                toastRes = R.string.paywall_activated;
                toastNonce++;
                finishNonce++;
                publish();
                break;
            case "not_paid":
                status = context.getString(R.string.paywall_not_paid);
                publish();
                break;
            case "device_mismatch":
                status = context.getString(R.string.paywall_device_mismatch);
                publish();
                break;
            case "unauthorized":
                status = context.getString(R.string.paywall_unauthorized);
                publish();
                break;
            case "invalid_device":
                status = context.getString(R.string.paywall_device_invalid);
                publish();
                break;
            default:
                status = result.message;
                publish();
                break;
        }
    }

    private void publish() {
        int used = Math.min(license.freeLimit(), settings.getDownloadUses());
        PaywallUiState value = new PaywallUiState(configured, busy, developer, devDismiss,
                initialEmail,
                context.getString(R.string.paywall_uses, used, license.freeLimit()),
                context.getString(R.string.paywall_device_id, license.deviceLabel()),
                status == null ? "" : status, emailError, codeError,
                emailErrorNonce, codeErrorNonce, sentNonce, toastRes, toastNonce, finishNonce);
        if (Looper.myLooper() == Looper.getMainLooper()) state.setValue(value);
        else state.postValue(value);
    }
}
