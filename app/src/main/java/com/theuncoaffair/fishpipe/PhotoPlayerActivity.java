package com.theuncoaffair.fishpipe;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.databinding.ActivityPhotoPlayerBinding;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.ui.DownloadOptionsSheet;
import com.theuncoaffair.fishpipe.ui.photoplayer.PhotoPlayerUiState;
import com.theuncoaffair.fishpipe.ui.photoplayer.PhotoPlayerViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.List;

import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class PhotoPlayerActivity extends BaseActivity {
    public static final String EXTRA_ITEM = "item";

    @Inject
    Settings settings;
    @Inject
    ResolveRepository resolver;
    @Inject
    @Named(AppModule.IO)
    ExecutorService io;

    private ActivityPhotoPlayerBinding binding;
    private PhotoPlayerViewModel viewModel;
    private GestureDetector gestures;
    private String lastImageUrl;
    private int lastToastNonce;
    private int lastFinishNonce;

    @Override
    protected int themeRes() {
        return ThemeManager.playerThemeRes(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPhotoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(PhotoPlayerViewModel.class);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Ui.statusBar(this, false);
        immersive();

        binding.photoBack.setOnClickListener(v -> finish());
        binding.photoPrevious.setOnClickListener(v -> slide(-1));
        binding.photoNext.setOnClickListener(v -> slide(1));
        binding.photoShare.setOnClickListener(v -> share());
        binding.photoTiktok.setOnClickListener(v -> openTikTok());
        binding.photoMore.setOnClickListener(this::showMore);

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent first, MotionEvent second,
                                   float velocityX, float velocityY) {
                if (first == null || second == null) return false;
                float dx = second.getX() - first.getX();
                float dy = second.getY() - first.getY();
                if (Math.abs(dx) < dp(40) || Math.abs(dx) <= Math.abs(dy)) return false;
                slide(dx < 0 ? 1 : -1);
                return true;
            }
        });

        PhotoPlayerUiState initial = viewModel.getState().getValue();
        if (initial != null) {
            lastToastNonce = initial.toastNonce;
            lastFinishNonce = initial.finishNonce;
        }
        viewModel.getState().observe(this, this::render);
        viewModel.initialize((MediaItem) getIntent().getSerializableExtra(EXTRA_ITEM));
    }

    private void render(PhotoPlayerUiState state) {
        binding.photoTitle.setText(state.title);
        binding.photoStory.setVisibility(state.story ? View.VISIBLE : View.GONE);
        if (state.total > 1) {
            binding.photoCount.setVisibility(View.VISIBLE);
            binding.photoCount.setText(getString(
                    R.string.photo_position, state.index + 1, state.total));
        } else {
            binding.photoCount.setVisibility(View.GONE);
        }
        if (!state.currentUrl.isEmpty() && !state.currentUrl.equals(lastImageUrl)) {
            lastImageUrl = state.currentUrl;
            ImageLoader.load(binding.photoImage, state.currentUrl, R.drawable.ic_image);
        }
        binding.photoImage.setContentDescription(
                getString(R.string.photo_number, state.index + 1));
        bindMeta(state.item);

        if (state.toastNonce > lastToastNonce) {
            lastToastNonce = state.toastNonce;
            if (state.toastRes != 0) Ui.toast(this, state.toastRes);
        }
        if (state.finishNonce > lastFinishNonce) {
            lastFinishNonce = state.finishNonce;
            finish();
        }
    }

    private void bindMeta(MediaItem item) {
        boolean visible = false;
        if (item != null) {
            long uploadedAt = item.createTime > 0 ? item.createTime * 1000L : 0L;
            visible = binding.photoMeta.bind(item.author, uploadedAt,
                    item.captionFromTitle(), item.hashtagsFromTitle());
        }
        binding.photoMetaPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void slide(int direction) {
        if (!viewModel.slide(direction)) return;
        int width = getResources().getDisplayMetrics().widthPixels;
        float in = direction > 0 ? width : -width;
        binding.photoImage.animate().cancel();
        binding.photoImage.setTranslationX(in);
        binding.photoImage.setAlpha(0f);
        binding.photoImage.animate().translationX(0f).alpha(1f).setDuration(170).start();
    }

    private MediaItem currentItem() {
        PhotoPlayerUiState state = viewModel.getState().getValue();
        return state == null ? null : state.item;
    }

    private String currentUrl() {
        PhotoPlayerUiState state = viewModel.getState().getValue();
        return state == null ? "" : state.currentUrl;
    }

    private void share() {
        String link = link();
        if (MediaItem.notEmpty(link)) Ui.shareText(this, link);
    }

    private void openTikTok() {
        MediaItem item = currentItem();
        String pageUrl = item == null ? "" : item.pageUrl;
        if (MediaItem.notEmpty(pageUrl)) Ui.openUrl(this, pageUrl);
        else Ui.toast(this, R.string.share_link_unavailable);
    }

    private String link() {
        MediaItem item = currentItem();
        if (item != null && MediaItem.notEmpty(item.pageUrl)) return item.pageUrl;
        return currentUrl();
    }

    private void showMore(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        int downloadId = 1;
        int copyId = 2;
        int shareId = 3;
        MediaItem item = currentItem();
        if (item != null
                && (item.hasContent() || item.hasMusic() || item.hasCover())) {
            menu.getMenu().add(0, downloadId, 0, R.string.download_options);
        }
        menu.getMenu().add(0, copyId, 1, R.string.copy_link);
        menu.getMenu().add(0, shareId, 2, R.string.share);
        menu.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == downloadId) {
                showDownloadOptions();
                return true;
            }
            if (menuItem.getItemId() == copyId) {
                String link = link();
                if (MediaItem.notEmpty(link)) Ui.copy(this, link, R.string.copied_link);
                return true;
            }
            if (menuItem.getItemId() == shareId) {
                share();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showDownloadOptions() {
        MediaItem item = currentItem();
        if (item == null) return;
        String link = link();
        if (hasLocalImages(item) && MediaItem.notEmpty(link)) {
            resolveDownloadOptions(link);
            return;
        }
        showDownloadOptions(item);
    }

    private boolean hasLocalImages(MediaItem item) {
        if (item == null || item.images == null) return false;
        for (String url : item.images) {
            if (MediaItem.notEmpty(url) && (url.startsWith("content:")
                    || url.startsWith("file:") || url.startsWith("/"))) return true;
        }
        return false;
    }

    private void resolveDownloadOptions(String link) {
        Ui.toast(this, R.string.resolving_link);
        io.execute(() -> {
            MediaItem resolved = null;
            try {
                resolved = resolver.resolve(link);
            } catch (Exception ignored) {
            }
            final MediaItem result = resolved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result != null && (result.hasContent() || result.hasMusic()
                        || result.hasCover())) {
                    showDownloadOptions(result);
                } else {
                    Ui.toast(this, R.string.resolve_failed);
                }
            });
        });
    }

    private void showDownloadOptions(MediaItem item) {
        if (item == null) return;
        DownloadOptionsSheet.show(this, item, settings,
                new DownloadOptionsSheet.Listener() {
                    @Override
                    public void onEnqueue(String kind, boolean preferHd, boolean captions,
                                          String captionMode) {
                        toastQueued(DownloadController.enqueue(PhotoPlayerActivity.this,
                                item, kind, preferHd, captions, captionMode));
                    }

                    @Override
                    public void onEnqueueImages(List<String> selected, boolean withMusic) {
                        toastQueued(DownloadController.enqueueImages(PhotoPlayerActivity.this,
                                item, selected, withMusic));
                    }

                    @Override
                    public void onEnqueueSlideshow(List<String> selected) {
                        toastQueued(DownloadController.enqueueSlideshow(
                                PhotoPlayerActivity.this, item));
                    }
                });
    }

    private void toastQueued(int count) {
        if (count > 0) Ui.downloadStarted(this, getString(R.string.download_queued_count, count));
        else Ui.toast(this, R.string.no_media);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestures != null) gestures.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.onResume();
    }

    @Override
    protected void onPause() {
        viewModel.onPause();
        super.onPause();
    }
}
