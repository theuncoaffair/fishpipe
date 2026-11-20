package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ActivitySettingsDetailBinding;
import com.theuncoaffair.fishpipe.databinding.ItemSettingsChoiceBinding;
import com.theuncoaffair.fishpipe.databinding.ItemSettingsValueBinding;
import com.theuncoaffair.fishpipe.ui.settingsdetail.SettingsDetailUiState;
import com.theuncoaffair.fishpipe.ui.settingsdetail.SettingsDetailViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Markdown;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;
import com.theuncoaffair.fishpipe.update.UpdatePrompt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class SettingsDetailActivity extends BaseActivity {
    public static final String EXTRA_KIND = "kind";
    public static final String KIND_APPEARANCE = "appearance";
    public static final String KIND_RESOLVER = "resolver";
    public static final String KIND_NOTIFICATIONS = "notifications";
    public static final String KIND_ABOUT = "about";
    public static final String KIND_UPDATE = "update";

    @Inject
    Settings settings;

    private ActivitySettingsDetailBinding binding;
    private SettingsDetailViewModel viewModel;
    private TextView updateStatus;
    private int lastUpdateNonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(SettingsDetailViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        String kind = getIntent().getStringExtra(EXTRA_KIND);
        if (KIND_APPEARANCE.equals(kind)) showAppearance();
        else if (KIND_RESOLVER.equals(kind)) showResolver();
        else if (KIND_NOTIFICATIONS.equals(kind)) showNotifications();
        else if (KIND_ABOUT.equals(kind)) showAbout();
        else showUpdate();

        SettingsDetailUiState initial = viewModel.getState().getValue();
        if (initial != null) lastUpdateNonce = initial.resultNonce;
        viewModel.getState().observe(this, this::render);
    }

    private void showAppearance() {
        binding.toolbar.setTitle(R.string.appearance);
        binding.appearanceGroup.setVisibility(View.VISIBLE);
        String current = settings.getTheme();
        choice(binding.rowThemeSystem, Settings.THEME_SYSTEM, current);
        choice(binding.rowThemeLight, Settings.THEME_LIGHT, current);
        choice(binding.rowThemeDark, Settings.THEME_DARK, current);
        choice(binding.rowThemeAmoled, Settings.THEME_AMOLED, current);
    }

    private void choice(ItemSettingsChoiceBinding row, String code, String current) {
        row.settingName.setText(ThemeManager.themeLabel(this, code));
        row.settingCheck.setText(R.string.check_mark);
        row.settingCheck.setVisibility(code.equals(current) ? View.VISIBLE : View.INVISIBLE);
        row.getRoot().setOnClickListener(v -> {
            int previousNightMode = AppCompatDelegate.getDefaultNightMode();
            settings.setTheme(code);
            ThemeManager.applyTheme(code);
            if (previousNightMode == ThemeManager.nightMode(code)) recreate();
        });
    }

    private void showResolver() {
        binding.toolbar.setTitle(R.string.resolver);
        binding.resolverGroup.setVisibility(View.VISIBLE);
        binding.resolverUrl.setText(settings.getCustomResolver());
        binding.resolverKey.setText(settings.getResolverKey());
        showAction(R.string.save, () -> {
            String value = text(binding.resolverUrl.getText() == null
                    ? "" : binding.resolverUrl.getText().toString()).trim();
            if (!value.isEmpty() && !value.regionMatches(true, 0, "https://", 0, 8)) {
                Ui.toast(this, R.string.custom_resolver_invalid);
                return;
            }
            settings.setCustomResolver(value);
            settings.setResolverKey(text(binding.resolverKey.getText() == null
                    ? "" : binding.resolverKey.getText().toString()));
            Ui.toast(this, R.string.settings_saved);
            finish();
        });
    }

    private void showNotifications() {
        binding.toolbar.setTitle(R.string.notification);
        binding.notificationsGroup.setVisibility(View.VISIBLE);
        boolean[] progress = {settings.isNotifyProgress()};
        boolean[] complete = {settings.isNotifyComplete()};
        boolean[] embedStart = {settings.isNotifyEmbedStart()};
        boolean[] embedFinished = {settings.isNotifyEmbedFinished()};
        bindBool(binding.rowNotifyProgress, R.string.notification_progress, progress);
        bindBool(binding.rowNotifyComplete, R.string.notification_complete, complete);
        bindBool(binding.rowNotifyEmbedStart, R.string.notify_embed_start, embedStart);
        bindBool(binding.rowNotifyEmbedFinished, R.string.notify_embed_finished, embedFinished);
        showAction(R.string.save, () -> {
            settings.setNotifyProgress(progress[0]);
            settings.setNotifyComplete(complete[0]);
            settings.setNotifyEmbedStart(embedStart[0]);
            settings.setNotifyEmbedFinished(embedFinished[0]);
            Ui.toast(this, R.string.notification_saved);
            finish();
        });
    }

    private void bindBool(ItemSettingsValueBinding row, int label, boolean[] value) {
        row.settingName.setText(label);
        row.settingValue.setText(value[0] ? R.string.yes : R.string.no);
        row.getRoot().setOnClickListener(v -> Ui.singleChoice(this, getString(label),
                new String[]{getString(R.string.yes), getString(R.string.no)},
                value[0] ? 0 : 1, (dialog, which) -> {
                    dialog.dismiss();
                    value[0] = which == 0;
                    row.settingValue.setText(value[0] ? R.string.yes : R.string.no);
                }));
    }

    private void showAbout() {
        binding.toolbar.setTitle(R.string.about);
        binding.aboutGroup.setVisibility(View.VISIBLE);
        binding.aboutVersion.setText(getString(R.string.version, viewModel.versionName()));
        markdown(binding.aboutWhatsNew, "about/whats_new.md");
        markdown(binding.aboutBugFixes, "about/bug_fixes.md");
    }

    private void showUpdate() {
        binding.toolbar.setTitle(R.string.check_updates);
        binding.updateGroup.setVisibility(View.VISIBLE);
        binding.updateVersion.setText(getString(R.string.version, viewModel.versionName()));
        updateStatus = binding.updateStatus;
        updateStatus.setText(viewModel.updateMessage());
        showAction(R.string.check_updates, () -> UpdatePrompt.checkNow(this, true));
    }

    private void render(SettingsDetailUiState state) {
        binding.detailAction.setEnabled(!state.checking);
        if (updateStatus != null) {
            updateStatus.setText(state.updateMessage);
        }
        if (state.resultNonce > lastUpdateNonce) {
            lastUpdateNonce = state.resultNonce;
            if (!state.downloadUrl.isEmpty()) {
                openDownloadPage(state.downloadUrl);
            }
        }
    }

    private void openDownloadPage(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Ui.toast(this, R.string.update_open_failed);
        }
    }

    private void showAction(int text, Runnable runnable) {
        binding.detailActionBar.setVisibility(View.VISIBLE);
        binding.detailAction.setText(text);
        binding.detailAction.setOnClickListener(v -> runnable.run());
    }

    private void markdown(TextView view, String asset) {
        view.setMovementMethod(LinkMovementMethod.getInstance());
        try {
            view.setText(Markdown.render(read(asset)));
        } catch (IOException e) {
            view.setText("");
        }
    }

    private String read(String path) throws IOException {
        InputStream in = getAssets().open(path);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
