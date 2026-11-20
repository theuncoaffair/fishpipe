package com.theuncoaffair.fishpipe;

import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ActivityDownloadSettingsBinding;
import com.theuncoaffair.fishpipe.ui.downloadsettings.DownloadSettingsUiState;
import com.theuncoaffair.fishpipe.ui.downloadsettings.DownloadSettingsViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class DownloadSettingsActivity extends BaseActivity {
    private ActivityDownloadSettingsBinding binding;
    private DownloadSettingsViewModel viewModel;

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDownloadSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(DownloadSettingsViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        configureRows();
        viewModel.getState().observe(this, this::render);
    }

    private void configureRows() {
        setRow(binding.rowDownloadPath, R.string.download_path, R.string.download_path_desc);
        setRow(binding.rowFolderOrganization, R.string.folder_organization, R.string.folder_organization_desc);
        setRow(binding.rowDefaultVideo, R.string.default_video, 0);
        setRow(binding.rowDefaultPhotos, R.string.default_photos, 0);
        setRow(binding.rowMaxTasks, R.string.max_tasks, 0);
        setRow(binding.rowSpeedLimit, R.string.speed_limit, 0);
        setRow(binding.rowNetwork, R.string.network, R.string.network_desc);
        setRow(binding.rowCaptionsEnabled, R.string.captions_download, 0);
        setRow(binding.rowToastEmbedStart, R.string.embed_toast_start, 0);
        setRow(binding.rowToastEmbedFinished, R.string.embed_toast_finished, 0);
        setRow(binding.rowShowDownloadOptions, R.string.show_download_options, 0);
        setRow(binding.rowToastDownloadStart, R.string.toast_download_start, 0);
        setRow(binding.rowToastDownloadEnd, R.string.toast_download_end, 0);

        binding.rowDownloadPath.getRoot().setOnClickListener(v -> editDownloadPath());
        binding.rowFolderOrganization.getRoot().setOnClickListener(v -> chooseFolderMode());
        binding.rowDefaultVideo.getRoot().setOnClickListener(v -> chooseDefaultVideo());
        binding.rowDefaultPhotos.getRoot().setOnClickListener(v -> chooseDefaultPhotos());
        binding.rowMaxTasks.getRoot().setOnClickListener(v -> chooseMaxTasks());
        binding.rowSpeedLimit.getRoot().setOnClickListener(v -> chooseSpeedLimit());
        binding.rowNetwork.getRoot().setOnClickListener(v -> chooseNetwork());
        binding.rowCaptionsEnabled.getRoot().setOnClickListener(v -> chooseYesNo(
                R.string.captions_download, current().captionsEnabled, viewModel::setCaptionsEnabled));
        binding.rowToastEmbedStart.getRoot().setOnClickListener(v -> chooseYesNo(
                R.string.embed_toast_start, current().toastEmbedStart, viewModel::setToastEmbedStart));
        binding.rowToastEmbedFinished.getRoot().setOnClickListener(v -> chooseYesNo(
                R.string.embed_toast_finished, current().toastEmbedFinished, viewModel::setToastEmbedFinished));
        binding.rowShowDownloadOptions.getRoot().setOnClickListener(v -> chooseYesNo(
                R.string.show_download_options, current().showDownloadOptions,
                viewModel::setShowDownloadOptions));
        binding.rowToastDownloadStart.getRoot().setOnClickListener(v -> chooseYesNo(
                R.string.toast_download_start, current().toastDownloadStart,
                viewModel::setToastDownloadStart));
        binding.rowToastDownloadEnd.getRoot().setOnClickListener(v -> chooseYesNo(
                R.string.toast_download_end, current().toastDownloadEnd,
                viewModel::setToastDownloadEnd));
    }

    private void setRow(com.theuncoaffair.fishpipe.databinding.SettingsRowBinding row,
                        int title, int desc) {
        row.title.setText(title);
        if (desc == 0) {
            row.desc.setVisibility(View.GONE);
        } else {
            row.desc.setText(desc);
            row.desc.setVisibility(View.VISIBLE);
        }
    }

    private DownloadSettingsUiState current() {
        DownloadSettingsUiState state = viewModel.getState().getValue();
        return state == null ? emptyState() : state;
    }

    private DownloadSettingsUiState emptyState() {
        return new DownloadSettingsUiState("", Settings.FOLDER_FLAT, Settings.VIDEO_STANDARD,
                Settings.PHOTO_ONLY, Settings.NETWORK_ANY, 3, 0, true, true, true, false, false, true);
    }

    private void render(DownloadSettingsUiState state) {
        binding.rowDownloadPath.value.setText(state.downloadPath.isEmpty()
                ? getString(R.string.download_path_auto) : state.downloadPath);
        binding.rowFolderOrganization.value.setText(folderLabel(state.folderMode));
        binding.rowDefaultVideo.value.setText(videoLabel(state.defaultVideo));
        binding.rowDefaultPhotos.value.setText(photosLabel(state.defaultPhotos));
        binding.rowMaxTasks.value.setText(getString(R.string.max_tasks_value, state.maxTasks));
        binding.rowSpeedLimit.value.setText(speedLabel(state.speedLimitKbps));
        binding.rowNetwork.value.setText(networkLabel(state.networkPolicy));
        binding.rowCaptionsEnabled.value.setText(yesNo(state.captionsEnabled));
        binding.rowToastEmbedStart.value.setText(yesNo(state.toastEmbedStart));
        binding.rowToastEmbedFinished.value.setText(yesNo(state.toastEmbedFinished));
        binding.rowShowDownloadOptions.value.setText(yesNo(state.showDownloadOptions));
        binding.rowToastDownloadStart.value.setText(yesNo(state.toastDownloadStart));
        binding.rowToastDownloadEnd.value.setText(yesNo(state.toastDownloadEnd));
    }

    private String yesNo(boolean value) {
        return getString(value ? R.string.yes : R.string.no);
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private void chooseYesNo(int title, boolean current, BoolSetter setter) {
        String[] labels = {getString(R.string.yes), getString(R.string.no)};
        Ui.singleChoice(this, getString(title), labels, current ? 0 : 1, (dialog, which) -> {
            dialog.dismiss();
            setter.set(which == 0);
        });
    }

    private void editDownloadPath() {
        DownloadSettingsUiState state = current();
        Ui.input(this, getString(R.string.download_path), state.downloadPath,
                getString(R.string.download_path_hint), value -> {
                    if (viewModel.saveDownloadPath(value)) {
                        Ui.toast(this, R.string.settings_saved);
                    } else {
                        Ui.toast(this, R.string.download_path_invalid);
                    }
                });
    }

    private void chooseFolderMode() {
        String[] codes = {Settings.FOLDER_FLAT, Settings.FOLDER_CREATOR};
        String[] labels = {getString(R.string.folder_flat), getString(R.string.folder_creator)};
        int checked = Settings.FOLDER_CREATOR.equals(current().folderMode) ? 1 : 0;
        Ui.singleChoice(this, getString(R.string.folder_organization), labels, checked,
                (dialog, which) -> {
                    dialog.dismiss();
                    viewModel.setFolderMode(codes[which]);
                });
    }

    private void chooseDefaultVideo() {
        String[] codes = {Settings.VIDEO_HD, Settings.VIDEO_STANDARD,
                Settings.VIDEO_AUDIO, Settings.VIDEO_COVER};
        String[] labels = new String[codes.length];
        int checked = 0;
        String current = current().defaultVideo;
        for (int i = 0; i < codes.length; i++) {
            labels[i] = videoLabel(codes[i]);
            if (codes[i].equals(current)) checked = i;
        }
        Ui.singleChoice(this, getString(R.string.default_video), labels, checked, (dialog, which) -> {
            dialog.dismiss();
            viewModel.setDefaultVideo(codes[which]);
        });
    }

    private void chooseDefaultPhotos() {
        String[] codes = {Settings.PHOTO_ONLY, Settings.PHOTO_SLIDESHOW, Settings.PHOTO_AUDIO};
        String[] labels = new String[codes.length];
        int checked = 0;
        String current = current().defaultPhotos;
        for (int i = 0; i < codes.length; i++) {
            labels[i] = photosLabel(codes[i]);
            if (codes[i].equals(current)) checked = i;
        }
        Ui.singleChoice(this, getString(R.string.default_photos), labels, checked, (dialog, which) -> {
            dialog.dismiss();
            viewModel.setDefaultPhotos(codes[which]);
        });
    }

    private String folderLabel(String code) {
        return Settings.FOLDER_CREATOR.equals(code)
                ? getString(R.string.folder_creator) : getString(R.string.folder_flat);
    }

    private String videoLabel(String code) {
        if (Settings.VIDEO_HD.equals(code)) return getString(R.string.default_video_1080);
        if (Settings.VIDEO_AUDIO.equals(code)) return getString(R.string.default_video_audio);
        if (Settings.VIDEO_COVER.equals(code)) return getString(R.string.default_video_cover);
        return getString(R.string.default_video_720);
    }

    private String photosLabel(String code) {
        if (Settings.PHOTO_SLIDESHOW.equals(code)) return getString(R.string.default_photos_slideshow);
        if (Settings.PHOTO_AUDIO.equals(code)) return getString(R.string.default_photos_audio_only);
        return getString(R.string.default_photos_only);
    }

    private void chooseMaxTasks() {
        String[] labels = new String[5];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(R.string.max_tasks_value, i + 1);
        }
        Ui.singleChoice(this, getString(R.string.max_tasks), labels,
                current().maxTasks - 1, (dialog, which) -> {
                    dialog.dismiss();
                    viewModel.setMaxTasks(which + 1);
                });
    }

    private void chooseSpeedLimit() {
        int[] values = {0, 512, 1024, 2048, 5120, 10240};
        String[] labels = new String[values.length];
        int checked = 0;
        int current = current().speedLimitKbps;
        for (int i = 0; i < values.length; i++) {
            labels[i] = speedLabel(values[i]);
            if (values[i] == current) checked = i;
        }
        Ui.singleChoice(this, getString(R.string.speed_limit), labels, checked, (dialog, which) -> {
            dialog.dismiss();
            viewModel.setSpeedLimitKbps(values[which]);
        });
    }

    private void chooseNetwork() {
        String[] codes = {Settings.NETWORK_ANY, Settings.NETWORK_WIFI, Settings.NETWORK_MOBILE};
        String[] labels = {getString(R.string.network_any), getString(R.string.network_wifi),
                getString(R.string.network_mobile)};
        int checked = 0;
        String current = current().networkPolicy;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(current)) checked = i;
        }
        Ui.singleChoice(this, getString(R.string.network), labels, checked, (dialog, which) -> {
            dialog.dismiss();
            viewModel.setNetworkPolicy(codes[which]);
        });
    }

    private String speedLabel(int kbps) {
        if (kbps <= 0) return getString(R.string.speed_unlimited);
        if (kbps >= 1024) return getString(R.string.speed_mb_value, kbps / 1024);
        return getString(R.string.speed_kb_value, kbps);
    }

    private String networkLabel(String policy) {
        if (Settings.NETWORK_WIFI.equals(policy)) return getString(R.string.network_wifi);
        if (Settings.NETWORK_MOBILE.equals(policy)) return getString(R.string.network_mobile);
        return getString(R.string.network_any);
    }
}
