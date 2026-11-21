package com.theuncoaffair.fishpipe.ui.downloads;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.theuncoaffair.fishpipe.CreatorVideosActivity;
import com.theuncoaffair.fishpipe.MainActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.analytics.InsightsRepository;
import com.theuncoaffair.fishpipe.data.CreatorInfo;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.databinding.FragmentDownloadsBinding;
import com.theuncoaffair.fishpipe.download.DownloadService;
import com.theuncoaffair.fishpipe.ui.DeleteDialogs;
import com.theuncoaffair.fishpipe.ui.DownloadMenu;
import com.theuncoaffair.fishpipe.util.MenuDividers;
import com.theuncoaffair.fishpipe.util.Ui;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class DownloadsFragment extends Fragment {
    private FragmentDownloadsBinding binding;
    private DownloadsViewModel viewModel;
    private DownloadAdapter downloadAdapter;
    private CreatorAdapter creatorAdapter;
    private boolean receiverRegistered;
    private boolean suppressTab;
    private int lastActionNonce = -1;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (viewModel != null) viewModel.reload();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDownloadsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DownloadsViewModel.class);
        downloadAdapter = new DownloadAdapter(new DownloadAdapter.Listener() {
            @Override
            public void onOpen(DownloadEntry entry) {
                DownloadMenu.open(requireContext(), entry, viewModel::reload);
            }

            @Override
            public void onLongPress(DownloadEntry entry) {
                viewModel.beginSelection(entry);
            }

            @Override
            public void onToggle(DownloadEntry entry) {
                viewModel.toggleDownload(entry);
            }

            @Override
            public void onCancel(DownloadEntry entry) {
                viewModel.cancel(entry);
            }

            @Override
            public void onPause(DownloadEntry entry) {
                viewModel.pauseOrResume(entry);
            }

            @Override
            public void onRetry(DownloadEntry entry) {
                viewModel.retry(entry);
            }

            @Override
            public void onMenu(View anchor, DownloadEntry entry) {
                DownloadMenu.show(requireContext(), anchor, entry,
                        new DownloadDb(requireContext()), viewModel::reload);
            }
        });
        creatorAdapter = new CreatorAdapter(new CreatorAdapter.Listener() {
            @Override
            public void onOpen(CreatorInfo creator) {
                openCreator(creator);
            }

            @Override
            public void onLongPress(CreatorInfo creator) {
                viewModel.beginSelection(creator);
            }

            @Override
            public void onToggle(CreatorInfo creator) {
                viewModel.toggleCreator(creator);
            }

            @Override
            public void onMenu(View anchor, CreatorInfo creator) {
                showCreatorMenu(anchor, creator);
            }
        });
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(downloadAdapter);
        binding.creatorList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.creatorList.setAdapter(creatorAdapter);
        binding.list.setHasFixedSize(true);
        binding.creatorList.setHasFixedSize(true);

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.creators_title));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.history_videos));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.history_images));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.history_music));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (suppressTab) return;
                viewModel.setFilter(typeForTab(tab.getPosition()));
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                viewModel.reload();
            }
        });

        binding.btnCancelSelection.setOnClickListener(v -> viewModel.clearSelection());
        binding.btnDeleteSelected.setOnClickListener(v -> deleteSelected());
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    public void onShown() {
        if (viewModel != null) viewModel.reload();
    }

    public boolean hasMenuItems() {
        DownloadsUiState state = state();
        return state != null && state.itemCount() > 0;
    }

    public boolean allMenuSelected() {
        DownloadsUiState state = state();
        return state != null && state.allSelected();
    }

    public String currentTabLabel() {
        DownloadsUiState state = state();
        return getString(tabLabelRes(state == null
                ? DownloadsUiState.FILTER_CREATORS : state.filter));
    }

    public void toggleMenuSelection() {
        DownloadsUiState state = state();
        if (state == null) return;
        if (state.allSelected()) viewModel.clearSelection();
        else viewModel.selectAll();
    }

    public void clearMenuTabHistory() {
        DownloadsUiState state = state();
        if (state != null) clearTabHistory(state.filter);
    }

    public void clearMenuHistory() {
        clearHistory();
    }

    @Nullable
    private DownloadsUiState state() {
        return viewModel == null ? null : viewModel.getState().getValue();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(requireContext(), progressReceiver,
                    new IntentFilter(DownloadService.ACTION_PROGRESS),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
        if (viewModel != null) viewModel.reload();
    }

    @Override
    public void onStop() {
        if (receiverRegistered) {
            try {
                requireContext().unregisterReceiver(progressReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void render(@NonNull DownloadsUiState state) {
        binding.loading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        boolean creators = state.isCreators();
        binding.list.setVisibility(creators ? View.GONE : View.VISIBLE);
        binding.creatorList.setVisibility(creators ? View.VISIBLE : View.GONE);
        binding.empty.setVisibility(state.itemCount() == 0 ? View.VISIBLE : View.GONE);
        binding.emptyTitle.setText(creators
                ? R.string.history_empty_creators : R.string.downloads_empty_title);
        binding.emptyBody.setText(creators
                ? R.string.history_empty_creators_body : R.string.downloads_empty_body);

        if (creators) {
            creatorAdapter.setItems(state.creators);
            creatorAdapter.setSelection(state.selectionMode, state.selectedCreators);
        } else {
            downloadAdapter.setItems(state.downloads);
            downloadAdapter.setSelection(state.selectionMode, state.selectedDownloads);
        }

        binding.selectionBar.setVisibility(state.selectionMode ? View.VISIBLE : View.GONE);
        binding.selectionCount.setText(
                getString(R.string.selected_count, state.selectedCount()));

        int index = indexForFilter(state.filter);
        TabLayout.Tab tab = binding.tabLayout.getTabAt(index);
        if (tab != null && !tab.isSelected()) {
            suppressTab = true;
            tab.select();
            suppressTab = false;
        }

        if (state.actionNonce > lastActionNonce) {
            lastActionNonce = state.actionNonce;
            handleAction(state);
        }
        if (isAdded()) requireActivity().invalidateOptionsMenu();
    }

    private void handleAction(@NonNull DownloadsUiState state) {
        switch (state.action) {
            case DELETED:
                Ui.toast(requireContext(), getString(R.string.deleted_count, state.actionCount));
                break;
            case HISTORY_CLEARED:
                Ui.toast(requireContext(), R.string.history_cleared);
                break;
            case NO_SELECTION:
                Ui.toast(requireContext(), R.string.no_items_selected);
                break;
            case PAUSED:
                Ui.toast(requireContext(), R.string.download_paused);
                break;
            case RESUMED:
                Ui.toast(requireContext(), R.string.download_resumed);
                break;
            case RETRYING:
                Ui.toast(requireContext(), R.string.download_retrying);
                break;
            case NONE:
            default:
                break;
        }
    }

    private void deleteSelected() {
        DownloadsUiState state = viewModel.getState().getValue();
        if (state == null) return;
        boolean creators = state.isCreators();
        DeleteDialogs.show(requireContext(), creators,
                () -> viewModel.deleteSelected(true),
                () -> viewModel.deleteSelected(false));
    }

    private void showCreatorMenu(View anchor, CreatorInfo creator) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        int profileId = 1;
        int analyzeId = 2;
        int openId = 3;
        int shareId = 4;
        int deleteId = 5;
        String profileUrl = "https://www.tiktok.com/@" + creator.name;
        popup.getMenu().add(0, profileId, 0, R.string.insights_open_profile);
        popup.getMenu().add(0, analyzeId, 1, R.string.insights_analyze);
        popup.getMenu().add(0, openId, 2, R.string.open);
        popup.getMenu().add(0, shareId, 3, R.string.share_link);
        popup.getMenu().add(1, deleteId, 4, R.string.delete);
        MenuDividers.enable(popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == profileId) {
                Ui.openUrl(requireContext(), profileUrl);
            } else if (id == analyzeId) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openInsights(
                            profileUrl, InsightsRepository.MODE_CREATOR);
                }
            } else if (id == openId) {
                openCreator(creator);
            } else if (id == shareId) {
                Ui.shareText(requireContext(), profileUrl);
            } else if (id == deleteId) {
                DeleteDialogs.show(requireContext(), true,
                        () -> viewModel.deleteCreator(creator, true),
                        () -> viewModel.deleteCreator(creator, false));
            }
            return true;
        });
        popup.show();
    }

    private void clearTabHistory(String filter) {
        String tabName = getString(tabLabelRes(filter));
        String title = getString(R.string.clear_tab_history, tabName);
        if (DownloadsUiState.FILTER_CREATORS.equals(filter)) {
            DeleteDialogs.show(requireContext(), title, R.string.clear_creators_body,
                    R.string.delete_history_and_files,
                    () -> viewModel.clearTabHistory(true),
                    () -> viewModel.clearTabHistory(false));
        } else {
            DeleteDialogs.show(requireContext(), title, 0,
                    R.string.delete_history_and_device,
                    () -> viewModel.clearTabHistory(true),
                    () -> viewModel.clearTabHistory(false));
        }
    }

    private void clearHistory() {
        DeleteDialogs.show(requireContext(), R.string.clear_history_choice, 0,
                R.string.delete_history_and_device,
                () -> viewModel.clearHistory(true),
                () -> viewModel.clearHistory(false));
    }

    private void openCreator(CreatorInfo creator) {
        Intent intent = new Intent(requireContext(), CreatorVideosActivity.class);
        intent.putExtra(CreatorVideosActivity.EXTRA_AUTHOR, creator.name);
        intent.putExtra(CreatorVideosActivity.EXTRA_AVATAR, creator.avatar);
        startActivity(intent);
    }

    private static String typeForTab(int position) {
        if (position == 1) return DownloadEntry.TYPE_VIDEO;
        if (position == 2) return DownloadEntry.TYPE_IMAGE;
        if (position == 3) return DownloadEntry.TYPE_MUSIC;
        return DownloadsUiState.FILTER_CREATORS;
    }

    private static int indexForFilter(String filter) {
        if (DownloadEntry.TYPE_VIDEO.equals(filter)) return 1;
        if (DownloadEntry.TYPE_IMAGE.equals(filter)) return 2;
        if (DownloadEntry.TYPE_MUSIC.equals(filter)) return 3;
        return 0;
    }

    @StringRes
    private static int tabLabelRes(String filter) {
        if (DownloadsUiState.FILTER_CREATORS.equals(filter)) return R.string.creators_title;
        if (DownloadEntry.TYPE_VIDEO.equals(filter)) return R.string.history_videos;
        if (DownloadEntry.TYPE_IMAGE.equals(filter)) return R.string.history_images;
        return R.string.history_music;
    }

    @Override
    public void onDestroyView() {
        if (receiverRegistered) {
            try {
                requireContext().unregisterReceiver(progressReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
        binding.list.setAdapter(null);
        binding.creatorList.setAdapter(null);
        binding = null;
        super.onDestroyView();
    }
}
