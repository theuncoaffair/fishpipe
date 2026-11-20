package com.theuncoaffair.fishpipe.ui.settings;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.repository.LicenseRepository;
import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class SettingsViewModel extends ViewModel {
    private final SettingsRepository settings;
    private final LicenseRepository license;
    private final Context context;
    private final MutableLiveData<SettingsUiState> state = new MutableLiveData<>();

    @Inject
    public SettingsViewModel(SettingsRepository settings, LicenseRepository license,
                             @ApplicationContext Context context) {
        this.settings = settings;
        this.license = license;
        this.context = context.getApplicationContext();
        state.setValue(read());
    }

    public LiveData<SettingsUiState> getState() {
        return state;
    }

    public void refresh() {
        state.setValue(read());
    }

    private SettingsUiState read() {
        return new SettingsUiState(
                settings.getLanguage(),
                settings.getTheme(),
                settings.getRegionCode(),
                settings.getRegionName(),
                settings.getFolderMode(),
                settings.getCustomResolver(),
                versionName(),
                settings.getMaxTasks(),
                license.remaining(),
                license.isProActive(),
                settings.isNotifyProgress(),
                settings.isNotifyComplete());
    }

    private String versionName() {
        try {
            String value = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return value == null ? "" : value;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "";
        }
    }
}
