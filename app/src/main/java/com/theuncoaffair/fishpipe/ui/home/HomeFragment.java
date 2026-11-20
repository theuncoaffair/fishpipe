package com.theuncoaffair.fishpipe.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.AudioPlayerActivity;
import com.theuncoaffair.fishpipe.BatchDownloadActivity;
import com.theuncoaffair.fishpipe.MainActivity;
import com.theuncoaffair.fishpipe.PhotoPlayerActivity;
import com.theuncoaffair.fishpipe.PlayerActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.analytics.InsightsRepository;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.databinding.FragmentHomeBinding;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.ui.DownloadGuard;
import com.theuncoaffair.fishpipe.ui.DownloadOptionsSheet;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class HomeFragment extends Fragment {
    @Inject Settings settings;

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private String pendingLink;
    private boolean pendingAutoDownload;
    private boolean bindingInput;
    private boolean inputActive;
    private boolean captionExpanded;
    private boolean firstRender = true;
    private int lastActionNonce;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        binding.inputLayout.setEndIconOnClickListener(v -> onInputAction());
        binding.inputLink.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!bindingInput) viewModel.setLink(s.toString());
                updateInputAction();
            }
        });
        binding.inputLink.setOnFocusChangeListener((v, hasFocus) -> {
            inputActive = hasFocus;
            updateInputAction();
        });
        binding.inputLink.setOnEditorActionListener((v, actionId, event) -> {
            resolveInput();
            return true;
        });
        binding.btnResetUsage.setOnClickListener(v -> viewModel.resetUsage());
        binding.btnResolve.setOnClickListener(v -> resolveInput());
        binding.btnUseClipboard.setOnClickListener(v -> viewModel.useClipboardLink());
        binding.btnDownloadVideo.setOnClickListener(v -> showDownloadOptions());
        binding.btnInsights.setOnClickListener(v -> openInsights());
        binding.btnBatch.setOnClickListener(v -> openBatch());
        binding.btnCopyLink.setOnClickListener(v -> copyLink());
        binding.btnOpenSource.setOnClickListener(v -> openSource());
        binding.resultPlay.setOnClickListener(v -> preview());
        binding.resultCaption.setOnClickListener(v -> toggleCaption());
        updateInputAction();
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        if (pendingLink != null) {
            setLinkAndResolve(pendingLink, pendingAutoDownload);
            pendingLink = null;
            pendingAutoDownload = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding == null) return;
        viewModel.refreshLicense();
        viewModel.onClipboardText(Ui.clipboard(requireContext()));
        binding.inputLink.clearFocus();
        inputActive = false;
        updateInputAction();
        if (getActivity() != null) Ui.hideKeyboard(getActivity());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public void onShown() {
        if (binding == null) return;
        binding.inputLink.clearFocus();
        inputActive = false;
        updateInputAction();
        if (getActivity() != null) Ui.hideKeyboard(getActivity());
    }

    public void setLinkAndResolve(String link, boolean autoDownload) {
        if (binding == null || viewModel == null) {
            pendingLink = link;
            pendingAutoDownload = autoDownload;
            return;
        }
        String value = link == null ? "" : link;
        setInput(value);
        viewModel.setLink(value);
        viewModel.resolve(value, autoDownload);
    }

    private void resolveInput() {
        String raw = text();
        if (raw.isEmpty()) {
            viewModel.resolveClipboard(Ui.clipboard(requireContext()));
            return;
        }
        viewModel.resolve(raw, false);
        if (getActivity() != null) Ui.hideKeyboard(getActivity());
    }

    private void onInputAction() {
        String value = text();
        if (value.isEmpty()) {
            String clip = Ui.clipboard(requireContext());
            if (clip == null || clip.trim().isEmpty()) {
                Ui.toast(requireContext(), R.string.clipboard_empty);
                return;
            }
            String pasted = clip.trim();
            setInput(pasted);
            viewModel.setLink(pasted);
            binding.inputLink.requestFocus();
            return;
        }
        if (binding.inputLink.hasFocus()) {
            setInput("");
            viewModel.clear();
            return;
        }
        Ui.copy(requireContext(), value);
        Ui.toast(requireContext(), R.string.copied);
    }

    private void updateInputAction() {
        if (binding == null) return;
        if (text().isEmpty()) {
            binding.inputLayout.setEndIconDrawable(R.drawable.ic_paste);
            binding.inputLayout.setEndIconContentDescription(R.string.paste_clipboard);
        } else if (inputActive) {
            binding.inputLayout.setEndIconDrawable(R.drawable.ic_delete);
            binding.inputLayout.setEndIconContentDescription(R.string.delete);
        } else {
            binding.inputLayout.setEndIconDrawable(R.drawable.ic_copy);
            binding.inputLayout.setEndIconContentDescription(R.string.copy_link);
        }
    }

    private void render(HomeUiState state) {
        if (binding == null) return;
        binding.freeDownloads.setText(state.pro
                ? getString(R.string.home_pro)
                : getString(R.string.home_free_left, state.remaining));
        binding.btnResolve.setEnabled(!state.resolving);
        binding.btnResolve.setText(state.resolving ? R.string.resolving : R.string.resolve);
        binding.progressRow.setVisibility(state.resolving ? View.VISIBLE : View.GONE);
        binding.bannerClipboard.setVisibility(state.clipboardVisible ? View.VISIBLE : View.GONE);

        String current = text();
        if (!current.equals(state.link)) {
            setInput(state.link);
        }

        MediaItem item = state.item;
        binding.resultCard.setVisibility(item == null ? View.GONE : View.VISIBLE);
        if (item == null) {
            firstRender = false;
            return;
        }
        renderItem(item);

        if (state.autoDownloadPending) {
            List<DownloadEntry> existing = autoDownloadExisting(item);
            DownloadGuard.confirm(requireContext(), existing,
                    viewModel::autoDownloadNow, null);
            viewModel.clearAutoDownloadPending();
        }
        if (state.error != HomeUiState.Error.NONE) {
            Ui.toast(requireContext(), messageFor(state.error));
            viewModel.consumeError();
        }
        if (firstRender) {
            lastActionNonce = state.actionNonce;
            firstRender = false;
        } else if (state.actionNonce != lastActionNonce) {
            lastActionNonce = state.actionNonce;
            if (state.action == HomeUiState.Action.DOWNLOAD_STARTED) {
                Ui.downloadStarted(requireContext());
            } else if (state.action == HomeUiState.Action.NO_MEDIA) {
                Ui.toast(requireContext(), R.string.no_media);
            }
        }
    }

    private void renderItem(MediaItem item) {
        String creator = item.author == null || item.author.isEmpty()
                ? getString(R.string.unknown_author)
                : getString(R.string.by_author, item.author);
        binding.resultAuthor.setText(creator);
        String date = item.createTime > 0
                ? Ui.formatUploadTime(requireContext(), item.createTime * 1000L) : "";
        binding.resultDate.setText(date);
        Ui.setVisible(binding.resultDate, !date.isEmpty());
        Ui.setVisible(binding.resultDot, !date.isEmpty());
        binding.resultCreatorRow.setVisibility(View.VISIBLE);

        String caption = item.captionFromTitle();
        binding.resultCaption.setText(caption);
        Ui.setVisible(binding.resultCaption, !caption.isEmpty());
        captionExpanded = false;
        binding.resultCaption.setMaxLines(2);
        binding.resultCaption.setEllipsize(TextUtils.TruncateAt.END);

        String hashtags = item.hashtagsFromTitle();
        binding.resultHashtags.setText(hashtags);
        Ui.setVisible(binding.resultHashtags, hashtags != null && !hashtags.isEmpty());

        StringBuilder meta = new StringBuilder();
        if (item.source != null && !item.source.isEmpty()) {
            meta.append(item.source.toUpperCase(Locale.ROOT));
        }
        if (item.id != null && !item.id.isEmpty()) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(item.id);
        }
        if (item.story) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(getString(R.string.story_label));
        }
        if (item.slideshow) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(getString(R.string.slideshow_label));
        }
        if (item.hasImages()) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(getString(R.string.images_found, item.images.size()));
        }
        if (item.videoHdUrl != null && !item.videoHdUrl.isEmpty()) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(getString(R.string.quality_available));
        }
        binding.resultMeta.setText(meta);

        String cover = item.coverUrl;
        if ((cover == null || cover.isEmpty()) && item.hasImages()) cover = item.images.get(0);
        binding.resultCover.setImageTintList(null);
        binding.resultCover.clearColorFilter();
        if (cover != null && !cover.isEmpty()) {
            ImageLoader.load(binding.resultCover, cover, R.drawable.ic_video);
        } else {
            binding.resultCover.setImageResource(R.drawable.ic_video);
            binding.resultCover.setColorFilter(ThemeManager.attrColor(requireContext(), R.attr.colorOutline));
        }

        Ui.setVisible(binding.resultPlay, item.hasVideo() || item.hasImages() || item.hasMusic());
        Ui.setVisible(binding.btnDownloadVideo,
                item.hasVideo() || item.hasMusic() || item.hasImages() || item.hasCover());
        Ui.setVisible(binding.btnInsights, MediaItem.notEmpty(item.pageUrl));
        Ui.setVisible(binding.btnBatch, MediaItem.notEmpty(item.author));
    }

    private void toggleCaption() {
        if (binding == null) return;
        if (binding.resultCaption.getLineCount() <= 2) return;
        captionExpanded = !captionExpanded;
        binding.resultCaption.setMaxLines(captionExpanded ? Integer.MAX_VALUE : 2);
        binding.resultCaption.setEllipsize(captionExpanded ? null : TextUtils.TruncateAt.END);
    }

    private void showDownloadOptions() {
        HomeUiState state = viewModel.getState().getValue();
        if (state == null || state.item == null) return;
        MediaItem item = state.item;
        DownloadOptionsSheet.show(requireContext(), item, settings,
                new DownloadOptionsSheet.Listener() {
                    @Override
                    public void onEnqueue(String kind, boolean preferHd, boolean captions,
                                          String captionMode) {
                        viewModel.enqueue(kind, preferHd, captions, captionMode);
                    }

                    @Override
                    public void onEnqueueImages(List<String> images, boolean withMusic) {
                        viewModel.enqueueImages(images, withMusic);
                    }

                    @Override
                    public void onEnqueueSlideshow(List<String> images) {
                        viewModel.enqueueSlideshow(images);
                    }
                });
    }

    private void openInsights() {
        HomeUiState state = viewModel.getState().getValue();
        if (state == null || state.item == null || !MediaItem.notEmpty(state.item.pageUrl)) return;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openInsights(
                    state.item.pageUrl, InsightsRepository.MODE_VIDEO);
        }
    }

    private void openBatch() {
        HomeUiState state = viewModel.getState().getValue();
        if (state == null || state.item == null) return;
        if (!MediaItem.notEmpty(state.item.author)) {
            Ui.toast(requireContext(), R.string.batch_link_missing);
            return;
        }
        Intent intent = new Intent(requireContext(), BatchDownloadActivity.class);
        intent.putExtra(BatchDownloadActivity.EXTRA_AUTHOR, state.item.author);
        intent.putExtra(BatchDownloadActivity.EXTRA_AVATAR, state.item.authorAvatar);
        startActivity(intent);
    }

    private void copyLink() {
        HomeUiState state = viewModel.getState().getValue();
        if (state == null || state.item == null) return;
        Ui.copy(requireContext(), state.item.pageUrl, R.string.copied_link);
        settings.setLastClipboard(state.item.pageUrl);
    }

    private void openSource() {
        HomeUiState state = viewModel.getState().getValue();
        if (state == null || state.item == null) return;
        Ui.openUrl(requireContext(), state.item.pageUrl);
    }

    private void preview() {
        HomeUiState state = viewModel.getState().getValue();
        if (state == null || state.item == null) return;
        MediaItem item = state.item;
        if (item.hasImages()) {
            Intent intent = new Intent(requireContext(), PhotoPlayerActivity.class);
            intent.putExtra(PhotoPlayerActivity.EXTRA_ITEM, item);
            startActivity(intent);
        } else if (item.hasVideo()) {
            Intent intent = new Intent(requireContext(), PlayerActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_URL, item.bestVideo(false));
            intent.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
            intent.putExtra(PlayerActivity.EXTRA_ITEM, item);
            startActivity(intent);
        } else if (item.hasMusic()) {
            Intent intent = new Intent(requireContext(), AudioPlayerActivity.class);
            intent.putExtra(AudioPlayerActivity.EXTRA_URL, item.musicUrl);
            intent.putExtra(AudioPlayerActivity.EXTRA_TITLE,
                    item.musicTitle == null || item.musicTitle.isEmpty()
                            ? item.title : item.musicTitle);
            intent.putExtra(AudioPlayerActivity.EXTRA_AUTHOR, item.musicAuthor);
            intent.putExtra(AudioPlayerActivity.EXTRA_COVER, item.musicCoverUrl);
            intent.putExtra(AudioPlayerActivity.EXTRA_PAGE_URL, item.pageUrl);
            intent.putExtra(AudioPlayerActivity.EXTRA_CAPTION, item.captionFromTitle());
            intent.putExtra(AudioPlayerActivity.EXTRA_HASHTAGS, item.hashtagsFromTitle());
            startActivity(intent);
        } else {
            Ui.openUrl(requireContext(), item.pageUrl);
        }
    }

    private List<DownloadEntry> autoDownloadExisting(MediaItem item) {
        String kind = "";
        String remote = "";
        if (item.hasVideo()) {
            String preference = settings.getDefaultVideo();
            if (Settings.VIDEO_HD.equals(preference)) {
                kind = DownloadController.KIND_HD;
                remote = item.videoHdUrl;
            } else if (Settings.VIDEO_STANDARD.equals(preference)) {
                kind = DownloadController.KIND_VIDEO;
                remote = item.videoUrl;
            }
        }
        if (kind.isEmpty()) return DownloadGuard.existingFor(requireContext(), item);
        return DownloadGuard.existingForOption(requireContext(), item, kind, remote);
    }

    private int messageFor(HomeUiState.Error error) {
        if (error == HomeUiState.Error.NETWORK) return R.string.network_error;
        if (error == HomeUiState.Error.STORY_EXPIRED) return R.string.story_expired;
        if (error == HomeUiState.Error.RESOLVE_FAILED) return R.string.resolve_failed;
        return R.string.invalid_link;
    }

    private String text() {
        return binding == null || binding.inputLink.getText() == null
                ? "" : binding.inputLink.getText().toString();
    }

    private void setInput(String value) {
        if (binding == null) return;
        if (value == null) value = "";
        if (value.equals(text())) return;
        bindingInput = true;
        binding.inputLink.setText(value);
        binding.inputLink.setSelection(value.length());
        bindingInput = false;
        updateInputAction();
    }
}
