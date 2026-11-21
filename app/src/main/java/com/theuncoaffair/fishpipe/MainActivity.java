package com.theuncoaffair.fishpipe;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.navigation.NavigationView;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ActivityMainBinding;
import com.theuncoaffair.fishpipe.databinding.NavHeaderMainBinding;
import com.theuncoaffair.fishpipe.download.ClipboardWatchService;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.ui.MarkdownDialog;
import com.theuncoaffair.fishpipe.ui.downloads.DownloadsFragment;
import com.theuncoaffair.fishpipe.ui.home.HomeFragment;
import com.theuncoaffair.fishpipe.ui.main.MainUiState;
import com.theuncoaffair.fishpipe.ui.main.MainViewModel;
import com.theuncoaffair.fishpipe.update.UpdatePrompt;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.Diagnostics;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class MainActivity extends BaseActivity {
    public static final String EXTRA_TAB = "extra_tab";
    public static final String EXTRA_LINK = "extra_link";
    public static final int TAB_HOME = 0;
    public static final int TAB_DOWNLOADS = 1;
    public static final int TAB_INSIGHTS = 2;

    private static final String TAG_HOME = "home";
    private static final String TAG_DOWNLOADS = "downloads";
    private static final String TAG_INSIGHTS = "insights";

    @Inject Settings settings;

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private HomeFragment home;
    private DownloadsFragment downloads;
    private InsightsFragment insights;
    private NavHeaderMainBinding headerBinding;
    private MaterialSwitch staySwitch;
    private MaterialSwitch autoDetectSwitch;
    private int currentTab = -1;
    private long lastBack;
    private String appliedLanguage;
    private String appliedTheme;
    private androidx.appcompat.app.AlertDialog overlayAlert;
    private boolean bindingSwitches;
    private boolean bindingNav;
    private boolean overlayPermissionRequested;
    private boolean overlaySettingsOpened;
    private boolean deepLinkHandled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationContentDescription(R.string.menu);
        applySystemBarInsets(binding.mainContent, false);
        applyBottomSystemBarInset(binding.bottomNav);
        applyBottomContentInset();
        Ui.statusBar(this, ThemeManager.isLight(this));
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        appliedLanguage = settings.getLanguage();
        appliedTheme = settings.getTheme();

        binding.toolbar.setNavigationOnClickListener(v -> openDrawer());
        binding.bottomNav.setOnItemSelectedListener(item -> {
            if (bindingNav) return true;
            int id = item.getItemId();
            if (id == R.id.nav_insights) showTab(TAB_INSIGHTS);
            else showTab(id == R.id.nav_downloads ? TAB_DOWNLOADS : TAB_HOME);
            return true;
        });
        setupDrawer();
        viewModel.getState().observe(this, this::applyState);

        int tab = getIntent().getIntExtra(EXTRA_TAB, TAB_HOME);
        showTab(tab == TAB_DOWNLOADS ? TAB_DOWNLOADS
                : tab == TAB_INSIGHTS ? TAB_INSIGHTS : TAB_HOME);
        handleIntent(getIntent());
        requestPermissions();
        if (Diagnostics.hasPendingCrash(this)) showCrashDialog();
        else if (!deepLinkHandled) showWhatsNewIfNeeded();
        viewModel.startDownloads();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    closeDrawer();
                    return;
                }
                if (currentTab != TAB_HOME) {
                    showTab(TAB_HOME);
                    return;
                }
                long now = now();
                if (now - lastBack < 2000L) {
                    finish();
                } else {
                    lastBack = now;
                    Ui.toast(MainActivity.this, R.string.exit_confirm);
                }
            }
        });
    }

    private void applyBottomContentInset() {
        binding.bottomNav.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                     oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            binding.fragmentContainer.setPadding(
                    binding.fragmentContainer.getPaddingLeft(),
                    binding.fragmentContainer.getPaddingTop(),
                    binding.fragmentContainer.getPaddingRight(),
                    height);
        });
    }

    private void setupDrawer() {
        headerBinding = NavHeaderMainBinding.bind(binding.navigationView.getHeaderView(0));
        bindDrawerInsets();
        staySwitch = headerBinding.switchStayInApp;
        autoDetectSwitch = headerBinding.switchAutoDetect;
        staySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingSwitches) return;
            if (checked) {
                viewModel.setStayInApp(true);
                ensureOverlayPermission();
            } else {
                setStayInApp(false);
            }
        });
        autoDetectSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (bindingSwitches) return;
            viewModel.setAutoDetect(checked);
        });
        binding.navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            closeDrawer();
            if (id == R.id.drawer_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.drawer_feedback) {
                feedback();
            } else if (id == R.id.drawer_recommend) {
                Ui.shareText(this, getString(R.string.recommend_text));
            } else if (id == R.id.drawer_how) {
                MarkdownDialog.showCloseOnly(this, R.string.how_to_title,
                        new String[]{"about/how_to_download.md"});
            } else if (id == R.id.drawer_whats_new) {
                showWhatsNew();
            } else if (id == R.id.drawer_group) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.join_group_title)
                        .setMessage(getString(R.string.join_group_body,
                                getString(R.string.feedback_email)))
                        .setPositiveButton(R.string.close, null)
                        .show();
            }
            return true;
        });
    }

    private void bindDrawerInsets() {
        View spacer = headerBinding.headerStatusBarSpacer;
        ViewCompat.setOnApplyWindowInsetsListener(binding.navigationView, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            ViewGroup.LayoutParams params = spacer.getLayoutParams();
            if (params.height != top) {
                params.height = top;
                spacer.setLayoutParams(params);
            }
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(0, 0, 0, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.navigationView);
    }

    private void applyState(@NonNull MainUiState state) {
        bindingSwitches = true;
        staySwitch.setChecked(state.stayInApp);
        autoDetectSwitch.setChecked(state.autoDetect);
        bindingSwitches = false;

        headerBinding.headerStatus.setText(state.pro
                ? getString(R.string.pro_status_pro)
                : getString(R.string.drawer_downloads_left, state.remaining));

        if (!state.language.equals(appliedLanguage) || !state.theme.equals(appliedTheme)) {
            appliedLanguage = state.language;
            appliedTheme = state.theme;
            recreate();
        }
    }

    private void ensureFragments() {
        FragmentManager fm = getSupportFragmentManager();
        home = (HomeFragment) fm.findFragmentByTag(TAG_HOME);
        downloads = (DownloadsFragment) fm.findFragmentByTag(TAG_DOWNLOADS);
        insights = (InsightsFragment) fm.findFragmentByTag(TAG_INSIGHTS);
        if (home != null && downloads != null) return;
        FragmentTransaction tx = fm.beginTransaction();
        if (home == null) {
            home = new HomeFragment();
            tx.add(R.id.fragment_container, home, TAG_HOME);
        }
        if (downloads == null) {
            downloads = new DownloadsFragment();
            tx.add(R.id.fragment_container, downloads, TAG_DOWNLOADS);
        }
        tx.commitNow();
    }

    private void showTab(int tab) {
        ensureFragments();
        if (tab == TAB_INSIGHTS && insights == null) {
            insights = new InsightsFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, insights, TAG_INSIGHTS)
                    .hide(insights)
                    .commitNow();
        }
        if (tab == currentTab) {
            if (tab == TAB_HOME) home.onShown();
            else if (tab == TAB_DOWNLOADS) downloads.onShown();
            return;
        }
        currentTab = tab;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.hide(home);
        tx.hide(downloads);
        if (insights != null) tx.hide(insights);
        tx.setMaxLifecycle(home, androidx.lifecycle.Lifecycle.State.STARTED);
        tx.setMaxLifecycle(downloads, androidx.lifecycle.Lifecycle.State.STARTED);
        if (insights != null) {
            tx.setMaxLifecycle(insights, androidx.lifecycle.Lifecycle.State.STARTED);
        }
        if (tab == TAB_DOWNLOADS) {
            tx.show(downloads);
            tx.setMaxLifecycle(downloads, androidx.lifecycle.Lifecycle.State.RESUMED);
            binding.toolbar.setTitle(R.string.tab_downloads);
        } else if (tab == TAB_INSIGHTS) {
            tx.show(insights);
            tx.setMaxLifecycle(insights, androidx.lifecycle.Lifecycle.State.RESUMED);
            binding.toolbar.setTitle(R.string.insights_title);
        } else {
            tx.show(home);
            tx.setMaxLifecycle(home, androidx.lifecycle.Lifecycle.State.RESUMED);
            binding.toolbar.setTitle(R.string.tab_home);
            home.onShown();
        }
        tx.commitNow();
        bindingNav = true;
        binding.bottomNav.setSelectedItemId(tab == TAB_DOWNLOADS ? R.id.nav_downloads
                : tab == TAB_INSIGHTS ? R.id.nav_insights : R.id.nav_home);
        bindingNav = false;
        invalidateOptionsMenu();
    }

    private void openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START);
    }

    private void closeDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void setStayInApp(boolean enabled) {
        viewModel.setStayInApp(enabled);
        if (!enabled) {
            overlayPermissionRequested = false;
            overlaySettingsOpened = false;
            if (overlayAlert != null && overlayAlert.isShowing()) overlayAlert.dismiss();
        }
        bindingSwitches = true;
        staySwitch.setChecked(enabled);
        bindingSwitches = false;
        ClipboardWatchService.sync(this);
    }

    private void ensureOverlayPermission() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            overlayPermissionRequested = false;
            overlaySettingsOpened = false;
            if (overlayAlert != null && overlayAlert.isShowing()) overlayAlert.dismiss();
            ClipboardWatchService.sync(this);
            return;
        }
        if (overlayAlert != null && overlayAlert.isShowing()) return;
        overlayPermissionRequested = true;
        overlaySettingsOpened = false;
        overlayAlert = new MaterialAlertDialogBuilder(this)
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
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            setStayInApp(false);
            Ui.toast(this, R.string.error_generic);
        }
    }

    public void openInsights(@Nullable String query, int mode) {
        showTab(TAB_INSIGHTS);
        if (insights != null) insights.analyzeQuery(query, mode);
    }

    private void feedback() {
        Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:" + getString(R.string.feedback_email)));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Ui.alert(this, getString(R.string.feedback), getString(R.string.feedback_email));
        }
    }

    private void showCrashDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.crash_title)
                .setMessage(R.string.crash_message)
                .setPositiveButton(R.string.share_log, (dialog, which) -> {
                    Diagnostics.share(this);
                    Diagnostics.markCrashReported(this);
                })
                .setNegativeButton(R.string.close, (dialog, which) ->
                        Diagnostics.markCrashReported(this))
                .setOnCancelListener(dialog -> Diagnostics.markCrashReported(this))
                .show();
    }

    private void showWhatsNewIfNeeded() {
        if (settings.isWhatsNewDismissed()) {
            UpdatePrompt.check(this);
            return;
        }
        showWhatsNew();
    }

    private void showWhatsNew() {
        MarkdownDialog.show(this, R.string.whats_new_title,
                new String[]{"about/whats_new.md", "about/bug_fixes.md"}, true,
                !settings.isWhatsNewDismissed(),
                () -> {
                    settings.setWhatsNewDismissed(true);
                    UpdatePrompt.check(this);
                },
                () -> {
                    settings.setWhatsNewDismissed(false);
                    UpdatePrompt.check(this);
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_insights_actions, menu);
        getMenuInflater().inflate(R.menu.menu_downloads_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean insightsVisible = currentTab == TAB_INSIGHTS && insights != null;
        boolean canToggle = insightsVisible && insights.hasCollapsibles();
        menu.findItem(R.id.action_insights_collapse)
                .setVisible(canToggle && !insights.allCollapsed());
        menu.findItem(R.id.action_insights_expand).setVisible(canToggle);

        boolean downloadsVisible = currentTab == TAB_DOWNLOADS && downloads != null;
        boolean hasDownloads = downloadsVisible && downloads.hasMenuItems();
        MenuItem select = menu.findItem(R.id.action_downloads_select);
        select.setVisible(hasDownloads);
        if (hasDownloads) {
            select.setTitle(downloads.allMenuSelected()
                    ? R.string.deselect_all : R.string.select_all);
        }
        MenuItem clearTab = menu.findItem(R.id.action_downloads_clear_tab);
        clearTab.setVisible(hasDownloads);
        if (hasDownloads) {
            clearTab.setTitle(getString(R.string.clear_tab_history,
                    downloads.currentTabLabel()));
        }
        menu.findItem(R.id.action_downloads_clear_all).setVisible(hasDownloads);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_insights_collapse) {
            if (insights != null) insights.collapseAll();
            return true;
        }
        if (item.getItemId() == R.id.action_insights_expand) {
            if (insights != null) insights.expandAll();
            return true;
        }
        if (item.getItemId() == R.id.action_downloads_select) {
            if (downloads != null) downloads.toggleMenuSelection();
            return true;
        }
        if (item.getItemId() == R.id.action_downloads_clear_tab) {
            if (downloads != null) downloads.clearMenuTabHistory();
            return true;
        }
        if (item.getItemId() == R.id.action_downloads_clear_all) {
            if (downloads != null) downloads.clearMenuHistory();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null && "fishpipe".equalsIgnoreCase(data.getScheme())) {
            deepLinkHandled = true;
            String host = data.getHost();
            if ("activate".equalsIgnoreCase(host) || "paywall".equalsIgnoreCase(host)) {
                Intent paywall = new Intent(this, PaywallActivity.class);
                paywall.putExtra(PaywallActivity.EXTRA_VOLUNTARY, true);
                String email = data.getQueryParameter("email");
                if (email != null && !email.trim().isEmpty()) {
                    paywall.putExtra(PaywallActivity.EXTRA_EMAIL, email.trim());
                }
                startActivity(paywall);
            }
            return;
        }
        int tab = intent.getIntExtra(EXTRA_TAB, -1);
        if (tab == TAB_HOME || tab == TAB_DOWNLOADS || tab == TAB_INSIGHTS) {
            showTab(tab);
            return;
        }
        String shared = intent.getStringExtra(EXTRA_LINK);
        if (shared != null && !shared.trim().isEmpty()) {
            intent.removeExtra(EXTRA_LINK);
            openLink(shared, null, false);
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            openLink(LinkUtils.findUrl(text), text, false);
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            openLink(intent.getData().toString(), null, false);
        }
    }

    private void openLink(String link, String fallback, boolean autoDownload) {
        if (link == null || link.isEmpty()) link = fallback;
        if (link == null || link.trim().isEmpty()) return;
        String url = LinkUtils.findUrl(link);
        if (!url.isEmpty()) settings.setLastClipboard(url);
        showTab(TAB_HOME);
        getSupportFragmentManager().executePendingTransactions();
        if (home != null) home.setLinkAndResolve(link, autoDownload);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.statusBar(this, ThemeManager.isLight(this));
        boolean canOverlay = android.provider.Settings.canDrawOverlays(this);
        if (overlayPermissionRequested && canOverlay) {
            overlayPermissionRequested = false;
            overlaySettingsOpened = false;
            if (overlayAlert != null && overlayAlert.isShowing()) overlayAlert.dismiss();
            ClipboardWatchService.sync(this);
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
        ClipboardWatchService.sync(this);
    }

    @Override
    protected void onPause() {
        closeDrawer();
        super.onPause();
    }

    private void requestPermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
            addIfMissing(missing, Manifest.permission.READ_MEDIA_IMAGES);
            addIfMissing(missing, Manifest.permission.READ_MEDIA_VIDEO);
            addIfMissing(missing, Manifest.permission.READ_MEDIA_AUDIO);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addIfMissing(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            addIfMissing(missing, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            addIfMissing(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), 10);
        }
    }

    private void addIfMissing(List<String> permissions, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }
}
