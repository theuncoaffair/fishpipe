package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;
import com.theuncoaffair.fishpipe.databinding.ActivityFloatingDownloadBinding;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.download.ClipboardTracker;
import com.theuncoaffair.fishpipe.download.ClipboardWatchService;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.ui.DownloadOptionsSheet;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class FloatingDownloadActivity extends BaseActivity {
    public static final String EXTRA_LINK = "extra_link";
    public static final String EXTRA_ITEM = "extra_item";
    public static final String EXTRA_COPIED_LINK = "extra_copied_link";
    public static final String EXTRA_CAPTURE_ONLY = "extra_capture_only";

    @Inject ResolveRepository resolver;
    @Inject Settings settings;

    @Inject
    @Named(AppModule.IO)
    ExecutorService io;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started;
    private boolean resumed;
    private boolean captureOnly;
    private boolean handedOff;
    private final Runnable beginFallback = this::maybeBegin;
    private final Runnable focusTimeout = () -> {
        if (!started && !isFinishing()) finish();
    };
    private String copiedUrl = "";

    @Override
    protected int themeRes() {
        return ThemeManager.transparentThemeRes(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        captureOnly = getIntent().getBooleanExtra(EXTRA_CAPTURE_ONLY, false);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        ActivityFloatingDownloadBinding binding = ActivityFloatingDownloadBinding.inflate(
                getLayoutInflater());
        setContentView(binding.getRoot());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        started = false;
        handedOff = false;
        captureOnly = intent.getBooleanExtra(EXTRA_CAPTURE_ONLY, false);
        maybeBegin();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) maybeBegin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        main.postDelayed(beginFallback, 250);
        main.postDelayed(focusTimeout, 2500);
    }

    @Override
    protected void onPause() {
        resumed = false;
        main.removeCallbacks(beginFallback);
        main.removeCallbacks(focusTimeout);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (!isChangingConfigurations()) {
            if (!captureOnly || !handedOff) ClipboardWatchService.setOverlaySuspended(false);
            finish();
        }
        super.onStop();
    }

    private void maybeBegin() {
        if (started || isFinishing()) return;
        if (!hasWindowFocus()) return;
        started = true;
        main.removeCallbacks(beginFallback);
        Serializable ready = getIntent().getSerializableExtra(EXTRA_ITEM);
        if (ready instanceof MediaItem) {
            MediaItem item = (MediaItem) ready;
            copiedUrl = getIntent().getStringExtra(EXTRA_COPIED_LINK);
            if (!MediaItem.notEmpty(copiedUrl)) copiedUrl = item.pageUrl;
            if (!MediaItem.notEmpty(item.pageUrl)) item.pageUrl = copiedUrl;
            showOptions(item);
            return;
        }
        String link = getIntent().getStringExtra(EXTRA_LINK);
        if (link == null || link.trim().isEmpty()) {
            link = ClipboardTracker.refresh(this);
        }
        String url = LinkUtils.findUrl(link);
        if (url.isEmpty()) {
            Ui.toast(this, R.string.floating_no_link);
            finish();
            return;
        }
        if (!LinkUtils.isSupported(url)) {
            Ui.toast(this, R.string.invalid_link);
            finish();
            return;
        }
        copiedUrl = url;
        settings.setLastClipboard(url);
        if (captureOnly) {
            handedOff = true;
            ClipboardWatchService.resolveFloating(this, url);
            finish();
            return;
        }
        resolve(url);
    }

    private void resolve(String url) {
        io.execute(() -> {
            MediaItem item = null;
            try {
                item = resolver.resolve(url);
            } catch (Exception ignored) {
            }
            MediaItem resolved = item;
            main.post(() -> showOptions(resolved));
        });
    }

    private void showOptions(MediaItem item) {
        if (isFinishing() || isDestroyed()) return;
        if (!resumed) {
            finish();
            return;
        }
        if (item == null) {
            Ui.toast(this, R.string.error_generic);
            finish();
            return;
        }
        if (!MediaItem.notEmpty(item.pageUrl)) item.pageUrl = copiedUrl;
        try {
            DownloadOptionsSheet.show(this, item, settings,
                new DownloadOptionsSheet.Listener() {
                    @Override
                    public void onEnqueue(String kind, boolean preferHd, boolean captions,
                                          String captionMode) {
                        settings.setFloatingDownload(copiedUrl, pageLink(item));
                        int count = DownloadController.enqueue(getApplicationContext(),
                                item, kind, preferHd, captions, captionMode);
                        if (count > 0) {
                            Ui.downloadStarted(getApplicationContext());
                        } else {
                            settings.clearFloatingDownload(pageLink(item));
                            Ui.toast(getApplicationContext(), R.string.no_media);
                        }
                    }

                    @Override
                    public void onEnqueueImages(List<String> images, boolean withMusic) {
                        settings.setFloatingDownload(copiedUrl, pageLink(item));
                        int count = DownloadController.enqueueImages(getApplicationContext(),
                                item, images, withMusic);
                        if (count > 0) {
                            Ui.downloadStarted(getApplicationContext());
                        } else {
                            settings.clearFloatingDownload(pageLink(item));
                            Ui.toast(getApplicationContext(), R.string.no_media);
                        }
                    }

                    @Override
                    public void onEnqueueSlideshow(List<String> images) {
                        settings.setFloatingDownload(copiedUrl, pageLink(item));
                        int count = DownloadController.enqueueSlideshow(getApplicationContext(), item);
                        if (count > 0) {
                            Ui.downloadStarted(getApplicationContext());
                        } else {
                            settings.clearFloatingDownload(pageLink(item));
                            Ui.toast(getApplicationContext(), R.string.no_media);
                        }
                    }
                },
                this::finish);
        } catch (Exception e) {
            Ui.toast(this, R.string.error_generic);
            finish();
        }
    }

    private String pageLink(MediaItem item) {
        return item != null && MediaItem.notEmpty(item.pageUrl) ? item.pageUrl : copiedUrl;
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(beginFallback);
        main.removeCallbacks(focusTimeout);
        if (!isChangingConfigurations() && (!captureOnly || !handedOff)) {
            ClipboardWatchService.setOverlaySuspended(false);
        }
        super.onDestroy();
    }
}
