package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.databinding.ActivityCreatorVideosBinding;
import com.theuncoaffair.fishpipe.ui.CreatorVideoAdapter;
import com.theuncoaffair.fishpipe.ui.DownloadMenu;
import com.theuncoaffair.fishpipe.ui.ThumbnailMenu;
import com.theuncoaffair.fishpipe.ui.creatorvideos.CreatorVideosUiState;
import com.theuncoaffair.fishpipe.ui.creatorvideos.CreatorVideosViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class CreatorVideosActivity extends BaseActivity {
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_AVATAR = "avatar";

    private ActivityCreatorVideosBinding binding;
    private CreatorVideosViewModel viewModel;
    private CreatorVideoAdapter adapter;
    private int lastActionNonce;
    private boolean resumedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreatorVideosBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(CreatorVideosViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemSelected);
        binding.creatorIdentity.setOnClickListener(v -> openBatch());
        binding.btnCancelSelection.setOnClickListener(v -> viewModel.clearSelection());
        binding.btnBatchDelete.setOnClickListener(v -> viewModel.deleteSelected());

        adapter = new CreatorVideoAdapter(new CreatorVideoAdapter.Listener() {
            @Override
            public void onOpen(DownloadEntry entry) {
                DownloadMenu.open(CreatorVideosActivity.this, entry, viewModel::refresh);
            }

            @Override
            public void onToggle(DownloadEntry entry) {
                viewModel.toggle(entry);
            }

            @Override
            public void onBeginSelection(DownloadEntry entry) {
                viewModel.toggle(entry);
            }

            @Override
            public void onMore(View anchor, DownloadEntry entry) {
                ThumbnailMenu.showEntry(CreatorVideosActivity.this, anchor, entry,
                        viewModel::refresh);
            }
        });
        binding.list.setLayoutManager(new GridLayoutManager(this, 3));
        binding.list.setAdapter(adapter);

        CreatorVideosUiState initial = viewModel.getState().getValue();
        if (initial != null) lastActionNonce = initial.actionNonce;
        viewModel.getState().observe(this, this::render);
        viewModel.load(getIntent().getStringExtra(EXTRA_AUTHOR),
                getIntent().getStringExtra(EXTRA_AVATAR));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce) {
            viewModel.refresh();
        } else {
            resumedOnce = true;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        CreatorVideosUiState state = viewModel.getState().getValue();
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
        CreatorVideosUiState state = viewModel.getState().getValue();
        if (state == null) return false;
        int id = item.getItemId();
        if (id == R.id.action_open_profile) {
            Ui.openUrl(this, "https://www.tiktok.com/@" + state.author);
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

    private void render(CreatorVideosUiState state) {
        binding.toolbar.setTitle(R.string.creator_history_title);
        binding.creatorName.setText(state.author.isEmpty()
                ? getString(R.string.creators_title) : "@" + state.author);
        binding.creatorCount.setText(getString(R.string.creator_video_count, state.items.size()));
        ImageLoader.load(binding.creatorAvatar, state.avatar, R.drawable.ic_app);

        adapter.submitList(state.items);
        adapter.setSelection(state.selected, state.selectionMode);

        binding.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.empty.setVisibility(!state.loading && state.items.isEmpty()
                ? View.VISIBLE : View.GONE);
        binding.selectionBar.setVisibility(state.selectionMode ? View.VISIBLE : View.GONE);
        binding.btnBatchDelete.setText(getString(R.string.delete_selected_count,
                state.selectedCount()));
        binding.btnBatchDelete.setEnabled(state.selectedCount() > 0 && !state.deleting);
        binding.btnBatchDelete.setAlpha(state.selectedCount() > 0 && !state.deleting ? 1f : 0.45f);
        binding.btnCancelSelection.setEnabled(!state.deleting);
        invalidateOptionsMenu();

        if (state.actionNonce > lastActionNonce) {
            lastActionNonce = state.actionNonce;
            Ui.toast(this, getString(R.string.deleted_count, state.deletedCount));
        }
    }

    private void openBatch() {
        CreatorVideosUiState state = viewModel.getState().getValue();
        if (state == null || state.author.trim().isEmpty()) {
            Ui.toast(this, R.string.batch_link_missing);
            return;
        }
        Intent intent = new Intent(this, BatchDownloadActivity.class);
        intent.putExtra(BatchDownloadActivity.EXTRA_AUTHOR, state.author);
        intent.putExtra(BatchDownloadActivity.EXTRA_AVATAR, state.avatar);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        CreatorVideosUiState state = viewModel.getState().getValue();
        if (state != null && state.selectionMode) {
            viewModel.clearSelection();
            return;
        }
        super.onBackPressed();
    }
}
