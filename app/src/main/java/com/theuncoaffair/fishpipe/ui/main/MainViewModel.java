package com.theuncoaffair.fishpipe.ui.main;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.repository.DownloadRepository;
import com.theuncoaffair.fishpipe.data.repository.LicenseRepository;
import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;
import com.theuncoaffair.fishpipe.download.ClipboardWatchService;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class MainViewModel extends ViewModel {
    private final SettingsRepository settings;
    private final LicenseRepository license;
    private final DownloadRepository downloads;
    private final Context context;
    private final MutableLiveData<MainUiState> state = new MutableLiveData<>();

    @Inject
    public MainViewModel(SettingsRepository settings, LicenseRepository license,
                         DownloadRepository downloads, @ApplicationContext Context context) {
        this.settings = settings;
        this.license = license;
        this.downloads = downloads;
        this.context = context.getApplicationContext();
        license.bootstrap();
        refresh();
    }

    public LiveData<MainUiState> getState() {
        return state;
    }

    public void refresh() {
        MainUiState current = new MainUiState(
                settings.getLanguage(),
                settings.getTheme(),
                settings.isStayInApp(),
                settings.isAutoDetect(),
                license.isProActive(),
                license.remaining(),
                downloads.runningCount());
        if (!current.equals(state.getValue())) {
            state.setValue(current);
        }
    }

    public void setStayInApp(boolean value) {
        settings.setStayInApp(value);
        ClipboardWatchService.sync(context);
        refresh();
    }

    public void setAutoDetect(boolean value) {
        settings.setAutoDetect(value);
        ClipboardWatchService.sync(context);
        refresh();
    }

    public boolean isPro() {
        return license.isProActive();
    }

    public void startDownloads() {
        if (downloads.runningCount() > 0) {
            downloads.start();
        }
    }
}
