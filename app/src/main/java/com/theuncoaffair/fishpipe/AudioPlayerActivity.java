package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.databinding.ActivityAudioPlayerBinding;
import com.theuncoaffair.fishpipe.ui.DeleteDialogs;
import com.theuncoaffair.fishpipe.ui.DownloadMenu;
import com.theuncoaffair.fishpipe.ui.DownloadOptionsSheet;
import com.theuncoaffair.fishpipe.ui.audioplayer.AudioPlayerArgs;
import com.theuncoaffair.fishpipe.ui.audioplayer.AudioPlayerUiState;
import com.theuncoaffair.fishpipe.ui.audioplayer.AudioPlayerViewModel;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.EdgeSwipeDetector;
import com.theuncoaffair.fishpipe.util.ImageLoader;
import com.theuncoaffair.fishpipe.util.MenuDividers;
import com.theuncoaffair.fishpipe.util.ThemeManager;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class AudioPlayerActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_COVER = "cover";
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_CAPTION = "caption";
    public static final String EXTRA_HASHTAGS = "hashtags";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_REPEAT = "repeat";

    @Inject
    Settings settings;
    @Inject
    ResolveRepository resolver;
    @Inject
    @Named(AppModule.IO)
    ExecutorService io;

    private ActivityAudioPlayerBinding binding;
    private AudioPlayerViewModel viewModel;
    private EdgeSwipeDetector edgeSwipe;
    private boolean seeking;
    private int lastToastNonce;
    private int lastFinishNonce;
    private int lastOpenNonce;
    private int lastPlaying = -1;
    private int lastRepeat = -1;
    private float lastSpeed = -1f;
    @Nullable
    private String lastCaption;
    @Nullable
    private String lastCover;
    @Nullable
    private String lastFileName;
    @Nullable
    private String lastTitle;
    @Nullable
    private String lastAuthor;
    @Nullable
    private String lastDate;

    @Override
    protected int themeRes() {
        return ThemeManager.playerThemeRes(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAudioPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        Ui.statusBar(this, false);
        edgeSwipe = new EdgeSwipeDetector(this, direction -> navigate(direction));

        viewModel = new ViewModelProvider(this).get(AudioPlayerViewModel.class);
        AudioPlayerUiState initial = viewModel.getState().getValue();
        if (initial != null) {
            lastToastNonce = initial.toastNonce;
            lastFinishNonce = initial.finishNonce;
            lastOpenNonce = initial.openNonce;
        }

        binding.audioBack.setOnClickListener(v -> finish());
        binding.audioShare.setOnClickListener(v -> shareAudio(viewModel.getAudioUrl()));
        binding.audioMore.setOnClickListener(this::showMore);
        binding.audioPlay.setOnClickListener(v -> viewModel.toggle());
        ImageButton previous = binding.audioPrevious;
        ImageButton next = binding.audioNext;
        previous.setOnTouchListener((v, event) -> handleHoldTouch(event, -1));
        next.setOnTouchListener((v, event) -> handleHoldTouch(event, 1));
        binding.audioRepeat.setOnClickListener(v -> viewModel.cycleRepeat());
        binding.audioSpeed.setOnClickListener(v -> viewModel.cycleSpeed());

        binding.audioSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                AudioPlayerUiState state = currentState();
                if (fromUser && state != null && state.durationMs > 0) {
                    binding.audioPosition.setText(
                            formatTime((long) state.durationMs * progress / 1000L));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                viewModel.seekToPercent(bar.getProgress());
            }
        });

        viewModel.getState().observe(this, this::render);
        viewModel.initialize(new AudioPlayerArgs(
                getIntent().getStringExtra(EXTRA_URL),
                getIntent().getStringExtra(EXTRA_TITLE),
                getIntent().getStringExtra(EXTRA_AUTHOR),
                getIntent().getStringExtra(EXTRA_COVER),
                getIntent().getStringExtra(EXTRA_PAGE_URL),
                getIntent().getStringExtra(EXTRA_FILE_NAME),
                getIntent().getIntExtra(EXTRA_REPEAT, AudioPlayerViewModel.REPEAT_OFF)));
    }

    private boolean handleHoldTouch(MotionEvent event, int direction) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return viewModel.onHoldDown(direction);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            viewModel.onHoldEnd(false);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            viewModel.onHoldEnd(true);
            return true;
        }
        return true;
    }

    private void render(AudioPlayerUiState state) {
        if (!TextUtils.equals(lastFileName, state.fileName)) {
            lastFileName = state.fileName;
            binding.audioFileName.setText(state.fileName);
        }
        if (!TextUtils.equals(lastTitle, state.title)) {
            lastTitle = state.title;
            binding.audioTitle.setText(state.title);
        }
        String author = state.author.trim().isEmpty()
                ? "" : getString(R.string.by_author, state.author);
        if (!TextUtils.equals(lastAuthor, author)) {
            lastAuthor = author;
            binding.audioAuthor.setText(author);
            binding.audioAuthor.setVisibility(author.isEmpty() ? View.GONE : View.VISIBLE);
        }
        binding.audioStory.setVisibility(state.story ? View.VISIBLE : View.GONE);

        String date = Ui.formatUploadTime(this, state.uploadedAt);
        if (!TextUtils.equals(lastDate, date)) {
            lastDate = date;
            binding.audioDate.setText(date);
            binding.audioDate.setVisibility(date.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (!TextUtils.equals(lastCover, state.coverUrl)) {
            lastCover = state.coverUrl;
            ImageLoader.load(binding.audioCover, state.coverUrl, R.drawable.ic_music);
        }

        binding.audioLoading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        int playing = state.playing ? 1 : 0;
        if (lastPlaying != playing) {
            lastPlaying = playing;
            binding.audioPlay.setImageResource(
                    state.playing ? R.drawable.ic_pause : R.drawable.ic_play);
            binding.audioPlay.setContentDescription(
                    getString(state.playing ? R.string.pause : R.string.play));
        }
        if (lastRepeat != state.repeatMode) {
            lastRepeat = state.repeatMode;
            updateRepeatTint(state.repeatMode);
        }
        if (Math.abs(lastSpeed - state.speed) > 0.001f) {
            lastSpeed = state.speed;
            binding.audioSpeed.setText(state.speedLabel);
        }
        if (!seeking) {
            if (state.durationMs > 0) {
                binding.audioSeek.setProgress(
                        (int) (state.positionMs * 1000L / state.durationMs));
            } else {
                binding.audioSeek.setProgress(0);
            }
            binding.audioPosition.setText(formatTime(state.positionMs));
        }
        binding.audioDuration.setText(formatTime(state.durationMs));

        if (!TextUtils.equals(lastCaption, state.caption)) {
            lastCaption = state.caption;
            binding.audioCaption.setText(state.caption);
            binding.audioCaption.setVisibility(
                    state.caption.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (state.toastNonce > lastToastNonce) {
            lastToastNonce = state.toastNonce;
            if (state.toastRes != 0) Ui.toast(this, state.toastRes);
        }
        if (state.finishNonce > lastFinishNonce) {
            lastFinishNonce = state.finishNonce;
            finish();
            return;
        }
        if (state.openNonce > lastOpenNonce) {
            lastOpenNonce = state.openNonce;
            if (state.openEntry != null) openEntry(state.openEntry);
        }
    }

    @Nullable
    private AudioPlayerUiState currentState() {
        return viewModel == null ? null : viewModel.getState().getValue();
    }

    private void updateRepeatTint(int repeatMode) {
        binding.audioRepeat.setImageResource(repeatMode == AudioPlayerViewModel.REPEAT_ONE
                ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        int color = getColor(repeatMode == AudioPlayerViewModel.REPEAT_OFF
                ? R.color.text_faint : R.color.primary);
        binding.audioRepeat.setColorFilter(color);
        binding.audioRepeat.setContentDescription(getString(
                repeatMode == AudioPlayerViewModel.REPEAT_OFF ? R.string.repeat_off
                        : repeatMode == AudioPlayerViewModel.REPEAT_ALL
                        ? R.string.repeat_all : R.string.repeat_one));
    }

    private void shareAudio(String url) {
        String link = viewModel.getPageUrl();
        if (TextUtils.isEmpty(link)) link = url;
        if (!TextUtils.isEmpty(link)) Ui.shareText(this, link);
        else Ui.toast(this, R.string.share_link_unavailable);
    }

    private void showMore(View anchor) {
        AudioPlayerUiState state = currentState();
        String pageUrl = state == null ? "" : state.pageUrl;
        String audioUrl = state == null ? "" : state.audioUrl;
        DownloadEntry localEntry = viewModel.getLocalEntry();
        PopupMenu menu = new PopupMenu(this, anchor);
        int openWithId = 1;
        int folderId = 2;
        int shareId = 3;
        int infoId = 4;
        int downloadId = 5;
        int deleteId = 6;
        if (localEntry != null) {
            menu.getMenu().add(0, openWithId, 0, R.string.open_with);
            menu.getMenu().add(0, folderId, 1, R.string.open_folder);
            menu.getMenu().add(0, shareId, 2, R.string.share);
            menu.getMenu().add(0, infoId, 3, R.string.media_info);
        }
        if (!audioUrl.isEmpty() || !pageUrl.trim().isEmpty()) {
            menu.getMenu().add(0, downloadId, 4, R.string.download_options);
        }
        if (localEntry != null) {
            menu.getMenu().add(1, deleteId, 5, R.string.delete);
        }
        MenuDividers.enable(menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == downloadId) {
                showDownloadOptions();
                return true;
            }
            if (localEntry == null) return false;
            if (item.getItemId() == openWithId) {
                Storage.openWith(this, localEntry);
                return true;
            }
            if (item.getItemId() == folderId) {
                Storage.openFolder(this, localEntry);
                return true;
            }
            if (item.getItemId() == shareId) {
                Storage.share(this, localEntry);
                return true;
            }
            if (item.getItemId() == infoId) {
                DownloadMenu.showInfo(this, localEntry);
                return true;
            }
            if (item.getItemId() == deleteId) {
                DeleteDialogs.show(this, false, () -> {
                    Storage.delete(this, localEntry.localUri);
                    new DownloadDb(this).delete(localEntry.rowId);
                    finish();
                }, () -> {
                    new DownloadDb(this).delete(localEntry.rowId);
                    finish();
                });
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showDownloadOptions() {
        AudioPlayerUiState state = currentState();
        String pageUrl = state == null ? "" : state.pageUrl;
        DownloadEntry localEntry = viewModel.getLocalEntry();
        MediaItem item = viewModel.downloadItem();
        if (localEntry != null && MediaItem.notEmpty(pageUrl)) {
            resolveDownloadOptions(item);
            return;
        }
        if (!item.hasMusic()) {
            if (MediaItem.notEmpty(pageUrl)) resolveDownloadOptions(null);
            else Ui.toast(this, R.string.no_media);
            return;
        }
        showDownloadOptions(item);
    }

    private void resolveDownloadOptions(@Nullable MediaItem fallback) {
        Ui.toast(this, R.string.resolving_link);
        String link = viewModel.getPageUrl();
        io.execute(() -> {
            MediaItem resolved = null;
            try {
                resolved = resolver.resolve(link);
            } catch (Exception ignored) {
            }
            final MediaItem result = resolved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result != null && result.hasMusic()) {
                    showDownloadOptions(result);
                } else if (fallback != null && fallback.hasMusic()) {
                    showDownloadOptions(fallback);
                } else {
                    Ui.toast(this, R.string.resolve_failed);
                }
            });
        });
    }

    private void showDownloadOptions(MediaItem item) {
        if (item == null || !item.hasMusic()) {
            Ui.toast(this, R.string.no_media);
            return;
        }
        DownloadOptionsSheet.show(this, item, settings,
                new DownloadOptionsSheet.Listener() {
                    @Override
                    public void onEnqueue(String kind, boolean preferHd, boolean captions,
                                          String captionMode) {
                        int count = DownloadController.enqueue(AudioPlayerActivity.this,
                                item, kind, preferHd, captions, captionMode);
                        if (count > 0) Ui.downloadStarted(AudioPlayerActivity.this);
                        else Ui.toast(AudioPlayerActivity.this, R.string.no_media);
                    }

                    @Override
                    public void onEnqueueImages(List<String> selected, boolean withMusic) {
                        Ui.toast(AudioPlayerActivity.this, R.string.no_media);
                    }

                    @Override
                    public void onEnqueueSlideshow(List<String> selected) {
                        Ui.toast(AudioPlayerActivity.this, R.string.no_media);
                    }
                });
    }

    private void navigate(int direction) {
        viewModel.navigate(direction);
    }

    private void openEntry(DownloadEntry entry) {
        Intent intent = new Intent(this, AudioPlayerActivity.class);
        intent.putExtra(EXTRA_URL, entry.localUri);
        intent.putExtra(EXTRA_TITLE, entry.title);
        intent.putExtra(EXTRA_AUTHOR, entry.author);
        intent.putExtra(EXTRA_COVER, entry.thumbUrl);
        intent.putExtra(EXTRA_PAGE_URL, entry.pageUrl);
        intent.putExtra(EXTRA_CAPTION, entry.caption);
        intent.putExtra(EXTRA_HASHTAGS, entry.hashtags);
        AudioPlayerUiState state = currentState();
        intent.putExtra(EXTRA_REPEAT, state == null
                ? AudioPlayerViewModel.REPEAT_OFF : state.repeatMode);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (edgeSwipe != null && edgeSwipe.onTouchEvent(event)) return true;
        return super.dispatchTouchEvent(event);
    }

    private String formatTime(long ms) {
        long total = Math.max(0, ms) / 1000L;
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    @Override
    protected void onPause() {
        viewModel.onActivityPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.onActivityResume();
    }
}
