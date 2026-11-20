package com.theuncoaffair.fishpipe.ui.downloadsettings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.repository.SettingsRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class DownloadSettingsViewModel extends ViewModel {
    private final SettingsRepository settings;
    private final MutableLiveData<DownloadSettingsUiState> state = new MutableLiveData<>();

    @Inject
    public DownloadSettingsViewModel(SettingsRepository settings) {
        this.settings = settings;
        state.setValue(read());
    }

    public LiveData<DownloadSettingsUiState> getState() {
        return state;
    }

    public void refresh() {
        publish();
    }

    public boolean saveDownloadPath(String value) {
        String cleaned = value == null ? "" : value.trim().replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.contains("..") || cleaned.contains(":")) {
            return false;
        }
        settings.setDownloadPath(cleaned);
        publish();
        return true;
    }

    public void setFolderMode(String mode) {
        settings.setFolderMode(mode);
        publish();
    }

    public void setDefaultVideo(String value) {
        settings.setDefaultVideo(value);
        publish();
    }

    public void setDefaultPhotos(String value) {
        settings.setDefaultPhotos(value);
        publish();
    }

    public void setMaxTasks(int value) {
        settings.setMaxTasks(value);
        publish();
    }

    public void setSpeedLimitKbps(int value) {
        settings.setSpeedLimitKbps(value);
        publish();
    }

    public void setNetworkPolicy(String policy) {
        settings.setNetworkPolicy(policy);
        publish();
    }

    public void setShowDownloadOptions(boolean value) {
        settings.setShowDownloadOptions(value);
        publish();
    }

    public void setToastDownloadStart(boolean value) {
        settings.setToastDownloadStart(value);
        publish();
    }

    public void setToastDownloadEnd(boolean value) {
        settings.setToastDownloadEnd(value);
        publish();
    }

    public void setToastEmbedStart(boolean value) {
        settings.setToastEmbedStart(value);
        publish();
    }

    public void setToastEmbedFinished(boolean value) {
        settings.setToastEmbedFinished(value);
        publish();
    }

    public void setCaptionsEnabled(boolean value) {
        settings.setCaptionsEnabled(value);
        publish();
    }

    private void publish() {
        state.setValue(read());
    }

    private DownloadSettingsUiState read() {
        return new DownloadSettingsUiState(
                settings.getDownloadPath(),
                settings.getFolderMode(),
                settings.getDefaultVideo(),
                settings.getDefaultPhotos(),
                settings.getNetworkPolicy(),
                settings.getMaxTasks(),
                settings.getSpeedLimitKbps(),
                settings.isShowDownloadOptions(),
                settings.isToastDownloadStart(),
                settings.isToastDownloadEnd(),
                settings.isToastEmbedStart(),
                settings.isToastEmbedFinished(),
                settings.isCaptionsEnabled());
    }
}
