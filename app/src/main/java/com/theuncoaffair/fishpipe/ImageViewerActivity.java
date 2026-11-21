package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.PopupMenu;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.databinding.ActivityImageViewerBinding;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.ui.DeleteDialogs;
import com.theuncoaffair.fishpipe.ui.DownloadMenu;
import com.theuncoaffair.fishpipe.ui.imageviewer.ImageViewerUiState;
import com.theuncoaffair.fishpipe.ui.imageviewer.ImageViewerViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.MenuDividers;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class ImageViewerActivity extends BaseActivity {
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_TITLE = "title";

    private ActivityImageViewerBinding binding;
    private ImageViewerViewModel viewModel;
    private GestureDetector gestures;
    private String lastRemoteUrl;
    private int lastFinishNonce;

    @Override
    protected int themeRes() {
        return ThemeManager.playerThemeRes(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(ImageViewerViewModel.class);

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
                navigate(dx < 0 ? 1 : -1);
                return true;
            }
        });

        binding.imageBack.setOnClickListener(v -> finish());
        binding.imagePrevious.setOnClickListener(v -> playPrevious());
        binding.imageNext.setOnClickListener(v -> playNext());
        binding.imageShare.setOnClickListener(v -> {
            DownloadEntry entry = currentEntry();
            if (entry != null && MediaItem.notEmpty(entry.pageUrl)) {
                Ui.shareText(this, entry.pageUrl);
            } else {
                Ui.toast(this, R.string.share_link_unavailable);
            }
        });
        binding.imageTiktok.setOnClickListener(v -> openTikTok());
        binding.imageMore.setOnClickListener(this::showMore);

        ImageViewerUiState initial = viewModel.getState().getValue();
        if (initial != null) lastFinishNonce = initial.finishNonce;
        viewModel.getState().observe(this, this::render);
        viewModel.initialize(getIntent().getStringExtra(EXTRA_URI),
                getIntent().getStringExtra(EXTRA_TITLE));
    }

    private void render(ImageViewerUiState state) {
        binding.imageTitle.setText(state.title);
        binding.imageTitle.setVisibility(state.titleVisible ? View.VISIBLE : View.GONE);
        binding.imageStory.setVisibility(state.story ? View.VISIBLE : View.GONE);
        binding.imageLoading.setVisibility(state.loading ? View.VISIBLE : View.GONE);

        if (state.remote) {
            String url = state.remoteUrl;
            if (url != null && !url.equals(lastRemoteUrl)) {
                lastRemoteUrl = url;
                ImageLoader.load(binding.image, url, R.drawable.ic_image);
            }
        } else if (state.bitmap != null) {
            binding.image.setImageBitmap(state.bitmap);
        } else if (!state.loading) {
            binding.image.setImageResource(R.drawable.ic_image);
        }

        bindMeta(state.entry);
        if (state.finishNonce > lastFinishNonce) {
            lastFinishNonce = state.finishNonce;
            finish();
        }
    }

    private void bindMeta(DownloadEntry entry) {
        boolean visible = false;
        if (entry != null) {
            String caption = MediaItem.captionForDisplay(MediaItem.notEmpty(entry.caption)
                    ? entry.caption : entry.title);
            String hashtags = MediaItem.notEmpty(entry.hashtags)
                    ? entry.hashtags : MediaItem.hashtagsOf(entry.title);
            visible = binding.imageMeta.bind(entry.author, entry.uploadedAt, caption, hashtags);
        }
        binding.imageMetaPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void playPrevious() {
        DownloadEntry previous = viewModel.previous();
        if (previous != null) openEntry(previous);
        else Ui.toast(this, R.string.no_previous_media);
    }

    private void playNext() {
        DownloadEntry next = viewModel.next();
        if (next != null) openEntry(next);
        else Ui.toast(this, R.string.no_next_media);
    }

    private void navigate(int direction) {
        if (direction < 0) playPrevious();
        else playNext();
    }

    private void openEntry(DownloadEntry next) {
        if (next == null) return;
        Intent intent = new Intent(this, ImageViewerActivity.class);
        intent.putExtra(EXTRA_URI, next.localUri);
        intent.putExtra(EXTRA_TITLE, next.title);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestures != null) gestures.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private DownloadEntry currentEntry() {
        ImageViewerUiState state = viewModel.getState().getValue();
        return state == null ? null : state.entry;
    }

    private void openTikTok() {
        DownloadEntry entry = currentEntry();
        if (entry != null && MediaItem.notEmpty(entry.pageUrl)) {
            Ui.openUrl(this, entry.pageUrl);
        } else {
            Ui.toast(this, R.string.share_link_unavailable);
        }
    }

    private void showMore(View anchor) {
        DownloadEntry entry = currentEntry();
        if (entry == null) {
            Ui.toast(this, R.string.file_not_found);
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        int openWithId = 1;
        int folderId = 2;
        int shareId = 3;
        int infoId = 4;
        int downloadId = 5;
        int deleteId = 6;
        menu.getMenu().add(0, openWithId, 0, R.string.open_with);
        menu.getMenu().add(0, folderId, 1, R.string.open_folder);
        menu.getMenu().add(0, shareId, 2, R.string.share);
        menu.getMenu().add(0, infoId, 3, R.string.media_info);
        if (MediaItem.notEmpty(entry.pageUrl)) {
            menu.getMenu().add(0, downloadId, 4, R.string.download_options);
        }
        menu.getMenu().add(1, deleteId, 5, R.string.delete);
        MenuDividers.enable(menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == openWithId) {
                Storage.openWith(this, entry);
                return true;
            }
            if (item.getItemId() == folderId) {
                Storage.openFolder(this, entry);
                return true;
            }
            if (item.getItemId() == shareId) {
                Storage.share(this, entry);
                return true;
            }
            if (item.getItemId() == infoId) {
                DownloadMenu.showInfo(this, entry);
                return true;
            }
            if (item.getItemId() == downloadId) {
                DownloadMenu.showOptions(this, entry, null);
                return true;
            }
            if (item.getItemId() == deleteId) {
                DeleteDialogs.show(this, false,
                        () -> viewModel.delete(true),
                        () -> viewModel.delete(false));
                return true;
            }
            return false;
        });
        menu.show();
    }
}
