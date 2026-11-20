package com.theuncoaffair.fishpipe.ui.settingsdetail;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.net.Http;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class SettingsDetailViewModel extends ViewModel {
    private final Context context;
    private final ExecutorService io;
    private final String versionName;
    private final long versionCode;
    private final MutableLiveData<SettingsDetailUiState> state = new MutableLiveData<>();

    @Inject
    public SettingsDetailViewModel(@ApplicationContext Context context,
                                   @Named("io") ExecutorService io) {
        this.context = context.getApplicationContext();
        this.io = io;
        PackageInfo info = packageInfo();
        this.versionName = info == null || info.versionName == null ? "" : info.versionName;
        this.versionCode = info == null ? 0 : longVersionCode(info);
        state.setValue(new SettingsDetailUiState(versionName,
                this.context.getString(R.string.update_idle), false, "", 0));
    }

    public LiveData<SettingsDetailUiState> getState() {
        return state;
    }

    public String versionName() {
        return versionName;
    }

    public String updateMessage() {
        SettingsDetailUiState current = state.getValue();
        return current == null ? context.getString(R.string.update_idle) : current.updateMessage;
    }

    public void checkForUpdates() {
        SettingsDetailUiState current = state.getValue();
        if (current != null && current.checking) return;
        String manifest = context.getString(R.string.update_manifest_url).trim();
        if (manifest.isEmpty()) {
            publish(context.getString(R.string.update_not_configured), false, "", false);
            return;
        }
        publish(context.getString(R.string.update_checking), true, "", false);
        io.execute(() -> {
            String message;
            String downloadUrl = "";
            boolean available = false;
            try {
                Http.Response response = Http.get(manifest, null);
                if (!response.ok()) throw new IOException("HTTP " + response.code);
                JSONObject json = new JSONObject(response.body);
                long latest = json.optLong("versionCode", 0);
                String name = json.optString("versionName", "");
                downloadUrl = json.optString("url", "");
                available = latest > versionCode && !downloadUrl.isEmpty();
                message = available
                        ? context.getString(R.string.update_available,
                        name.isEmpty() ? String.valueOf(latest) : name)
                        : context.getString(R.string.update_up_to_date);
            } catch (Exception e) {
                message = context.getString(R.string.update_check_failed);
            }
            publish(message, false, downloadUrl, available);
        });
    }

    private void publish(String message, boolean checking, String downloadUrl, boolean result) {
        SettingsDetailUiState current = state.getValue();
        int nonce = current == null ? 0 : current.resultNonce;
        if (result) nonce++;
        SettingsDetailUiState next = new SettingsDetailUiState(versionName, message,
                checking, downloadUrl, nonce);
        if (checking || message.equals(context.getString(R.string.update_not_configured))) {
            state.setValue(next);
        } else {
            state.postValue(next);
        }
    }

    private PackageInfo packageInfo() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long longVersionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }
}
