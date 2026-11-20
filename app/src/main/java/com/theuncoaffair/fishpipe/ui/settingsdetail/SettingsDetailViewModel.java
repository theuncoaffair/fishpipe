package com.theuncoaffair.fishpipe.ui.settingsdetail;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class SettingsDetailViewModel extends ViewModel {
    private final Context context;
    private final String versionName;
    private final long versionCode;
    private final MutableLiveData<SettingsDetailUiState> state = new MutableLiveData<>();

    @Inject
    public SettingsDetailViewModel(@ApplicationContext Context context) {
        this.context = context.getApplicationContext();
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
