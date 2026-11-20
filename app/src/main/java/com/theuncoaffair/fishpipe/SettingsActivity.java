package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ActivitySettingsBinding;
import com.theuncoaffair.fishpipe.pricing.AnnualPrice;
import com.theuncoaffair.fishpipe.databinding.SettingsRowBinding;
import com.theuncoaffair.fishpipe.region.RegionActivity;
import com.theuncoaffair.fishpipe.region.RegionRepository;
import com.theuncoaffair.fishpipe.ui.settings.SettingsUiState;
import com.theuncoaffair.fishpipe.ui.settings.SettingsViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Diagnostics;
import com.theuncoaffair.fishpipe.util.LocaleHelper;
import com.theuncoaffair.fishpipe.util.ThemeManager;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class SettingsActivity extends BaseActivity {
    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        configureRows();
        viewModel.getState().observe(this, this::render);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void configureRows() {
        setRow(binding.rowPro, R.string.pro);
        setRow(binding.rowLanguage, R.string.language);
        setRow(binding.rowDownload, R.string.download);
        setRow(binding.rowPrivacy, R.string.privacy);
        setRow(binding.rowTheme, R.string.theme);
        setRow(binding.rowRegion, R.string.region_change);
        setRow(binding.rowResolver, R.string.resolver);
        setRow(binding.rowNotification, R.string.notification);
        setRow(binding.rowDisclaimer, R.string.disclaimer);
        setRow(binding.rowAbout, R.string.about);
        setRow(binding.rowUpdate, R.string.check_updates);
        setRow(binding.rowDiagnostics, R.string.diagnostics);
        binding.rowUpdate.value.setText(R.string.update_idle);

        binding.rowPro.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, ProActivity.class)));
        binding.rowLanguage.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, LanguageActivity.class)));
        binding.rowDownload.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, DownloadSettingsActivity.class)));
        binding.rowPrivacy.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyActivity.class)));
        binding.rowTheme.getRoot().setOnClickListener(v ->
                openDetail(SettingsDetailActivity.KIND_APPEARANCE));
        binding.rowRegion.getRoot().setOnClickListener(v -> {
            Intent intent = new Intent(this, RegionActivity.class);
            intent.putExtra(RegionActivity.EXTRA_CHANGE, true);
            startActivity(intent);
        });
        binding.rowResolver.getRoot().setOnClickListener(v ->
                openDetail(SettingsDetailActivity.KIND_RESOLVER));
        binding.rowNotification.getRoot().setOnClickListener(v ->
                openDetail(SettingsDetailActivity.KIND_NOTIFICATIONS));
        binding.rowDisclaimer.getRoot().setOnClickListener(v -> {
            Intent intent = new Intent(this, LegalActivity.class);
            intent.putExtra(LegalActivity.EXTRA_KIND, LegalActivity.KIND_DISCLAIMER);
            startActivity(intent);
        });
        binding.rowAbout.getRoot().setOnClickListener(v ->
                openDetail(SettingsDetailActivity.KIND_ABOUT));
        binding.rowUpdate.getRoot().setOnClickListener(v ->
                openDetail(SettingsDetailActivity.KIND_UPDATE));
        binding.rowDiagnostics.getRoot().setOnClickListener(v -> Diagnostics.share(this));
    }

    private void setRow(SettingsRowBinding row, int title) {
        row.title.setText(title);
        row.desc.setVisibility(View.GONE);
    }

    private void render(SettingsUiState state) {
        binding.rowLanguage.value.setText(LocaleHelper.label(this, state.language));
        binding.rowPro.value.setText(state.proActive
                ? getString(R.string.pro_status_pro)
                : getString(R.string.pro_status_free, state.remaining,
                        AnnualPrice.forRegion(state.regionCode).display()));
        binding.rowTheme.value.setText(ThemeManager.themeLabel(this, state.theme));
        String region = state.regionCode;
        binding.rowRegion.value.setText(region.isEmpty()
                ? getString(R.string.not_set)
                : state.regionName.isEmpty() ? RegionRepository.nameFor(region) : state.regionName);
        String folder = Settings.FOLDER_CREATOR.equals(state.folderMode)
                ? getString(R.string.folder_creator) : getString(R.string.folder_flat);
        binding.rowDownload.value.setText(getString(R.string.download_settings_summary,
                state.maxTasks, folder));
        binding.rowResolver.value.setText(state.customResolver.isEmpty()
                ? getString(R.string.not_set) : state.customResolver);
        binding.rowNotification.value.setText(state.notificationsEnabled()
                ? R.string.enabled : R.string.disabled);
        binding.rowAbout.value.setText(getString(R.string.version, state.versionName));
    }

    private void openDetail(String kind) {
        Intent intent = new Intent(this, SettingsDetailActivity.class);
        intent.putExtra(SettingsDetailActivity.EXTRA_KIND, kind);
        startActivity(intent);
    }
}
