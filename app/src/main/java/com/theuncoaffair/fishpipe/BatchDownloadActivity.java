package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.ActivityBatchDownloadBinding;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.ui.BatchVideoAdapter;
import com.theuncoaffair.fishpipe.ui.DownloadOptionsSheet;
import com.theuncoaffair.fishpipe.ui.ThumbnailMenu;
import com.theuncoaffair.fishpipe.ui.batchdownload.BatchDownloadUiState;
import com.theuncoaffair.fishpipe.ui.batchdownload.BatchDownloadViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class BatchDownloadActivity extends BaseActivity {
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_AVATAR = "avatar";

    @Inject Settings settings;

    private ActivityBatchDownloadBinding binding;
    private BatchDownloadViewModel viewModel;
    private BatchVideoAdapter adapter;
    private int lastActionNonce;
    private int lastMessageNonce;
    private int lastOpenNonce;
    private int lastOptionsNonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBatchDownloadBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(BatchDownloadViewModel.class);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);
        binding.creatorIdentity.setOnClickListener(v -> openProfile());
        binding.btnRetry.setOnClickListener(v -> viewModel.retry());
        binding.btnCancel.setOnClickListener(v -> viewModel.clearSelection());
        binding.btnBatchDownload.setOnClickListener(v -> viewModel.prepareOptions());

        adapter = new BatchVideoAdapter(this, new BatchVideoAdapter.Listener() {
            @Override
            public void onOpen(@NonNull MediaItem item) {
                viewModel.requestOpen(item);
            }

            @Override
            public void onToggle(@NonNull MediaItem item) {
                viewModel.toggle(item);
            }

            @Override
            public void onBeginSelection(@NonNull MediaItem item) {
                viewModel.beginSelection(item);
            }

            @Override
            public void onMore(@NonNull View anchor, @NonNull MediaItem item) {
                ThumbnailMenu.showMedia(BatchDownloadActivity.this, anchor, item,
                        () -> viewModel.requestOpen(item), () -> viewModel.remove(item));
            }
        });
        GridLayoutManager layout = new GridLayoutManager(this, 3);
        binding.list.setLayoutManager(layout);
        binding.list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                                       @NonNull View view,
                                       @NonNull RecyclerView parent,
                                       @NonNull RecyclerView.State state) {
                int spacing = Math.round(2 * getResources().getDisplayMetrics().density);
                outRect.set(spacing / 2, spacing / 2, spacing / 2, spacing / 2);
            }
        });
        binding.list.setAdapter(adapter);

        BatchDownloadUiState initial = viewModel.getState().getValue();
        if (initial != null) {
            lastActionNonce = initial.actionNonce;
            lastMessageNonce = initial.messageNonce;
            lastOpenNonce = initial.openNonce;
            lastOptionsNonce = initial.optionsNonce;
        }
        viewModel.getState().observe(this, this::render);
        viewModel.load(getIntent().getStringExtra(EXTRA_AUTHOR),
                getIntent().getStringExtra(EXTRA_AVATAR));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        BatchDownloadUiState state = viewModel.getState().getValue();
        MenuItem select = menu.findItem(R.id.action_select_all);
        if (select != null) {
            boolean hasItems = state != null && !state.items.isEmpty();
            select.setVisible(hasItems);
            select.setTitle(state != null && state.allSelected()
                    ? R.string.deselect_all : R.string.select_all);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private boolean onMenuItemSelected(@NonNull MenuItem item) {
        BatchDownloadUiState state = viewModel.getState().getValue();
        if (state == null) return false;
        int id = item.getItemId();
        if (id == R.id.action_open_profile) {
            openProfile();
            return true;
        }
        if (id == R.id.action_share_profile) {
            Ui.shareText(this, "https://www.tiktok.com/@" + state.author);
            return true;
        }
        if (id == R.id.action_select_all) {
            if (state.allSelected()) viewModel.clearSelection();
            else viewModel.selectAll();
            return true;
        }
        return false;
    }

    private void render(BatchDownloadUiState state) {
        binding.creatorName.setText(getString(R.string.batch_creator_handle, state.author));
        binding.creatorCount.setText(state.items.isEmpty()
                && (state.loading || !state.errorMessage.isEmpty())
                ? "" : getString(R.string.batch_loaded_count, state.items.size()));
        ImageLoader.load(binding.creatorAvatar, state.avatar, R.drawable.ic_app);

        adapter.submitList(state.items);
        adapter.setSelection(state.selectedKeys, state.selectionMode);

        binding.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        boolean empty = !state.loading && state.items.isEmpty()
                && !state.errorMessage.isEmpty();
        binding.emptyPanel.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.empty.setText(state.errorMessage);
        binding.creatorCount.setVisibility(state.selectionMode ? View.GONE : View.VISIBLE);
        binding.btnCancel.setVisibility(state.selectionMode ? View.VISIBLE : View.GONE);
        binding.btnBatchDownload.setEnabled(!state.batchRunning && state.selectedCount() > 0);
        binding.btnBatchDownload.setAlpha(
                !state.batchRunning && state.selectedCount() > 0 ? 1f : 0.45f);
        binding.btnBatchDownload.setText(state.batchRunning
                ? getString(R.string.resolving_link)
                : state.selectedCount() > 0
                ? getString(R.string.batch_download_selected, state.selectedCount())
                : getString(R.string.download_selected));
        invalidateOptionsMenu();

        if (state.messageNonce > lastMessageNonce) {
            lastMessageNonce = state.messageNonce;
            if (state.messageRes != 0) Ui.toast(this, state.messageRes);
        }
        if (state.actionNonce > lastActionNonce) {
            lastActionNonce = state.actionNonce;
            if (state.startedCount > 0) {
                Ui.downloadStarted(this, getString(R.string.batch_started, state.startedCount));
            } else {
                Ui.toast(this, R.string.no_media);
            }
        }
        if (state.openNonce > lastOpenNonce) {
            lastOpenNonce = state.openNonce;
            open(state.openItem);
        }
        if (state.optionsNonce > lastOptionsNonce) {
            lastOptionsNonce = state.optionsNonce;
            showDownloadOptions(state);
        }
    }

    private void openProfile() {
        BatchDownloadUiState state = viewModel.getState().getValue();
        if (state == null || state.author.isEmpty()) return;
        Ui.openUrl(this, "https://www.tiktok.com/@" + state.author);
    }

    private void open(MediaItem item) {
        if (item == null) return;
        if (item.hasImages()) {
            Intent intent = new Intent(this, PhotoPlayerActivity.class);
            intent.putExtra(PhotoPlayerActivity.EXTRA_ITEM, item);
            startActivity(intent);
            return;
        }
        if (item.hasVideo()) {
            play(item, item.videoUrl);
            return;
        }
        Ui.toast(this, R.string.no_media);
    }

    private void play(MediaItem item, String url) {
        if (!MediaItem.notEmpty(url)) {
            Ui.toast(this, R.string.no_media);
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, url);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
        intent.putExtra(PlayerActivity.EXTRA_ITEM, item);
        intent.putExtra(PlayerActivity.EXTRA_BATCH, true);
        startActivity(intent);
    }

    private void showDownloadOptions(@NonNull BatchDownloadUiState state) {
        MediaItem sample = state.optionsSample;
        List<MediaItem> selected = state.optionsSelected;
        if (sample == null || selected.isEmpty()) {
            Ui.toast(this, R.string.no_items_selected);
            return;
        }
        DownloadOptionsSheet.showBatch(this, sample, selected, settings,
                new DownloadOptionsSheet.Listener() {
                    @Override
                    public void onEnqueue(String kind, boolean preferHd, boolean captions,
                                          String captionMode) {
                        viewModel.enqueueSelected(selected, kind, preferHd, captions, captionMode);
                    }

                    @Override
                    public void onEnqueueImages(List<String> images, boolean withMusic) {
                        viewModel.enqueueSelected(selected, DownloadController.KIND_IMAGES,
                                false, false, Settings.CAPTION_EMBED);
                    }

                    @Override
                    public void onEnqueueSlideshow(List<String> images) {
                        viewModel.enqueueSelected(selected, DownloadController.KIND_IMAGES,
                                false, false, Settings.CAPTION_EMBED);
                    }
                });
    }

    @Override
    public void onBackPressed() {
        BatchDownloadUiState state = viewModel.getState().getValue();
        if (state != null && state.selectionMode) {
            viewModel.clearSelection();
            return;
        }
        super.onBackPressed();
    }
}
