package com.theuncoaffair.fishpipe.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.navigation.NavigationView;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.SettingsActivity;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.NavHeaderMainBinding;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.download.ClipboardWatchService;
import com.theuncoaffair.fishpipe.ui.main.MainUiState;
import com.theuncoaffair.fishpipe.ui.main.MainViewModel;
import com.theuncoaffair.fishpipe.util.Ui;

public final class SidePanel {
    public interface Listener {
        void onLanguageOrThemeChanged();
    }

    private final AppCompatActivity activity;
    private final DrawerLayout drawerLayout;
    private final NavigationView navigationView;
    private final MainViewModel viewModel;
    private final Listener listener;
    private final Settings settings;
    private final NavHeaderMainBinding header;
    private MaterialSwitch staySwitch;
    private MaterialSwitch autoDetectSwitch;
    private MaterialSwitch autoDownloadSwitch;
    private boolean binding;
    private AlertDialog overlayAlert;
    private boolean overlayPermissionRequested;
    private boolean overlaySettingsOpened;
    private String appliedLanguage;
    private String appliedTheme;

    public SidePanel(AppCompatActivity activity, DrawerLayout drawerLayout,
                     NavigationView navigationView, MainViewModel viewModel,
                     @Nullable Listener listener) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.navigationView = navigationView;
        this.viewModel = viewModel;
        this.listener = listener;
        this.settings = AppGraph.get(activity).settings();
        this.header = NavHeaderMainBinding.bind(navigationView.getHeaderView(0));
        this.appliedLanguage = settings.getLanguage();
        this.appliedTheme = settings.getTheme();
    }

    public void bind() {
        applyInsets();
        staySwitch = header.switchStayInApp;
        autoDetectSwitch = header.switchAutoDetect;
        autoDownloadSwitch = header.switchAutoDownload;
        staySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (checked) {
                viewModel.setStayInApp(true);
                ensureOverlayPermission();
            } else {
                setStayInApp(false);
            }
        });
        autoDetectSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            viewModel.setAutoDetect(checked);
        });
        autoDownloadSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            viewModel.setAutoDownload(checked);
        });
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            close();
            if (id == R.id.drawer_settings) {
                activity.startActivity(new Intent(activity, SettingsActivity.class));
            } else if (id == R.id.drawer_feedback) {
                feedback();
            } else if (id == R.id.drawer_recommend) {
                Ui.shareText(activity, activity.getString(R.string.recommend_text));
            } else if (id == R.id.drawer_how) {
                MarkdownDialog.showCloseOnly(activity, R.string.how_to_title,
                        new String[]{"about/how_to_download.md"});
            } else if (id == R.id.drawer_whats_new) {
                showWhatsNew();
            } else if (id == R.id.drawer_group) {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.join_group_title)
                        .setMessage(activity.getString(R.string.join_group_body,
                                activity.getString(R.string.feedback_email)))
                        .setPositiveButton(R.string.close, null)
                        .show();
            }
            return true;
        });
        viewModel.getState().observe(activity, this::applyState);
    }

    public void open() {
        drawerLayout.openDrawer(GravityCompat.START);
    }

    public boolean isOpen() {
        return drawerLayout.isDrawerOpen(GravityCompat.START);
    }

    public void close() {
        if (isOpen()) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    public void onResume() {
        boolean canOverlay = android.provider.Settings.canDrawOverlays(activity);
        if (overlayPermissionRequested && canOverlay) {
            overlayPermissionRequested = false;
            overlaySettingsOpened = false;
            if (overlayAlert != null && overlayAlert.isShowing()) overlayAlert.dismiss();
            ClipboardWatchService.sync(activity);
        } else if (overlayPermissionRequested && overlaySettingsOpened && !canOverlay) {
            overlayPermissionRequested = false;
            overlaySettingsOpened = false;
            setStayInApp(false);
        }
        MainUiState state = viewModel.getState().getValue();
        if (state != null && state.stayInApp && !canOverlay && !overlayPermissionRequested) {
            setStayInApp(false);
        }
        viewModel.refresh();
        ClipboardWatchService.sync(activity);
    }

    private void applyInsets() {
        View spacer = header.getRoot().findViewById(R.id.header_status_bar_spacer);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {
            if (spacer != null) {
                int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                ViewGroup.LayoutParams params = spacer.getLayoutParams();
                if (params.height != top) {
                    params.height = top;
                    spacer.setLayoutParams(params);
                }
            }
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(0, 0, 0, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(navigationView);
    }

    private void applyState(MainUiState state) {
        binding = true;
        staySwitch.setChecked(state.stayInApp);
        autoDetectSwitch.setChecked(state.autoDetect);
        autoDownloadSwitch.setChecked(state.autoDownload);
        binding = false;

        header.headerStatus.setText(state.pro
                ? activity.getString(R.string.pro_status_pro)
                : activity.getString(R.string.drawer_downloads_left, state.remaining));

        if (!state.language.equals(appliedLanguage) || !state.theme.equals(appliedTheme)) {
            appliedLanguage = state.language;
            appliedTheme = state.theme;
            if (listener != null) listener.onLanguageOrThemeChanged();
        }
    }

    private void setStayInApp(boolean enabled) {
        viewModel.setStayInApp(enabled);
        if (!enabled) {
            overlayPermissionRequested = false;
            overlaySettingsOpened = false;
            if (overlayAlert != null && overlayAlert.isShowing()) overlayAlert.dismiss();
        }
        binding = true;
        staySwitch.setChecked(enabled);
        binding = false;
    }

    private void ensureOverlayPermission() {
        if (android.provider.Settings.canDrawOverlays(activity)) {
            overlayPermissionRequested = false;
            overlaySettingsOpened = false;
            if (overlayAlert != null && overlayAlert.isShowing()) overlayAlert.dismiss();
            ClipboardWatchService.sync(activity);
            return;
        }
        if (overlayAlert != null && overlayAlert.isShowing()) return;
        overlayPermissionRequested = true;
        overlaySettingsOpened = false;
        overlayAlert = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.floating_permission_title)
                .setMessage(R.string.floating_permission_body)
                .setPositiveButton(R.string.floating_permission_allow, (dialog, which) -> {
                    overlaySettingsOpened = true;
                    openOverlaySettings();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        overlayAlert.setOnDismissListener(dialog -> {
            overlayAlert = null;
            if (overlayPermissionRequested && !overlaySettingsOpened) {
                overlayPermissionRequested = false;
                setStayInApp(false);
            }
        });
        overlayAlert.show();
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Exception e) {
            setStayInApp(false);
            Ui.toast(activity, R.string.error_generic);
        }
    }

    private void feedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + activity.getString(R.string.feedback_email)));
        intent.putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.feedback_subject));
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            Ui.alert(activity, activity.getString(R.string.feedback),
                    activity.getString(R.string.feedback_email));
        }
    }

    private void showWhatsNew() {
        MarkdownDialog.show(activity, R.string.whats_new_title,
                new String[]{"about/whats_new.md", "about/bug_fixes.md"}, true,
                !settings.isWhatsNewDismissed(),
                () -> settings.setWhatsNewDismissed(true),
                () -> settings.setWhatsNewDismissed(false));
    }
}
