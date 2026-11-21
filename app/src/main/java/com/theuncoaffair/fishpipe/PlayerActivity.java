package com.theuncoaffair.fishpipe;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.lifecycle.ViewModelProvider;

import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.data.repository.ResolveRepository;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.databinding.ActivityPlayerBinding;
import com.theuncoaffair.fishpipe.download.CaptionBuilder;
import com.theuncoaffair.fishpipe.download.CaptionEmbedService;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.ui.DeleteDialogs;
import com.theuncoaffair.fishpipe.ui.DownloadMenu;
import com.theuncoaffair.fishpipe.ui.DownloadOptionsSheet;
import com.theuncoaffair.fishpipe.ui.player.PlayerArgs;
import com.theuncoaffair.fishpipe.ui.player.PlayerUiState;
import com.theuncoaffair.fishpipe.ui.player.PlayerViewModel;
import com.theuncoaffair.fishpipe.net.Http;
import com.theuncoaffair.fishpipe.net.LinkUtils;
import com.theuncoaffair.fishpipe.util.BaseActivity;
import com.theuncoaffair.fishpipe.util.EdgeSwipeDetector;
import com.theuncoaffair.fishpipe.util.MediaMetaView;
import com.theuncoaffair.fishpipe.util.MenuDividers;
import com.theuncoaffair.fishpipe.util.ThemeManager;

import timber.log.Timber;
import com.theuncoaffair.fishpipe.util.Ui;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class PlayerActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ITEM = "item";
    public static final String EXTRA_BATCH = "batch";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_REPEAT = "repeat";

    @Inject
    Settings settings;
    @Inject
    ResolveRepository resolver;
    @Inject
    @Named(AppModule.IO)
    ExecutorService io;

    private ActivityPlayerBinding binding;
    private VideoView video;
    private MediaPlayer mediaPlayer;
    private ProgressBar loading;
    private SeekBar seek;
    private TextView position;
    private TextView duration;
    private MediaMetaView meta;
    private TextView captionView;
    private String lastCaptionText = "";
    private ImageButton play;
    private ImageButton mute;
    private ImageButton repeat;
    private TextView speedView;
    private PlayerViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean prepared;
    private boolean playRequested;
    private boolean headerRetry;
    private Uri videoUri;
    private boolean resumed;
    private boolean seeking;
    private EdgeSwipeDetector edgeSwipe;
    private int durationMs;
    private int holdDir;
    private int holdTarget;
    private int holdStartPos;
    private long lastHoldSeekAt;
    private boolean holdWasPlaying;
    private boolean holdActive;
    private boolean holdSpeedMode;
    private float holdRate = 2f;
    private long holdDownAt;
    private int lastToastNonce;
    private int lastOpenNonce;
    private int lastRepeatMode = -1;
    private float lastSpeed = -1f;
    private boolean lastMuted;
    private ProgressBar embedProgress;
    private final Runnable holdTick = new Runnable() {
        @Override
        public void run() {
            if (!holdActive) return;
            updateHoldSeek();
            handler.postDelayed(this, 80L);
        }
    };
    private final Runnable holdStart = () -> {
        if (holdDir == 0 || !prepared) return;
        holdActive = true;
        holdStartPos = holdTarget;
        holdDownAt = now();
        lastHoldSeekAt = 0;
        holdRate = Math.min(8f, Math.max(2f, viewModel.getSpeed() * 4f));
        holdSpeedMode = holdDir > 0 && applyHoldSpeed(holdRate);
        if (holdSpeedMode) {
            if (!video.isPlaying()) {
                video.start();
                playRequested = true;
                startTicker();
            }
        } else {
            if (prepared) {
                video.start();
                playRequested = true;
                startTicker();
            }
            handler.post(holdTick);
        }
        updatePlayIcon();
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void stopTicker() {
        handler.removeCallbacks(ticker);
    }

    @Override
    protected int themeRes() {
        return ThemeManager.playerThemeRes(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemBarInsets(binding.getRoot());
        edgeSwipe = new EdgeSwipeDetector(this, this::navigate);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.statusBar(this, false);

        video = binding.video;
        loading = binding.playerLoading;
        seek = binding.playerSeek;
        position = binding.playerPosition;
        duration = binding.playerDuration;
        meta = binding.playerMeta;
        captionView = binding.playerCaption;
        embedProgress = binding.playerEmbedProgress;
        play = binding.playerPlay;
        mute = binding.playerMute;
        repeat = binding.playerRepeat;
        speedView = binding.playerSpeed;
        viewModel = new ViewModelProvider(this).get(PlayerViewModel.class);
        PlayerUiState initial = viewModel.getState().getValue();
        if (initial != null) {
            lastToastNonce = initial.toastNonce;
            lastOpenNonce = initial.openNonce;
        }

        speedView.setOnClickListener(v -> viewModel.cycleSpeed());
        binding.playerMore.setOnClickListener(this::showMore);
        binding.playerDownload.setOnClickListener(v -> showDownloadOptions());
        binding.playerBack.setOnClickListener(v -> finish());
        binding.playerShare.setOnClickListener(v -> {
            String link = viewModel.getDownloadLink();
            if (MediaItem.notEmpty(link)) Ui.shareText(this, link);
            else Ui.toast(this, R.string.share_link_unavailable);
        });
        binding.playerTiktok.setOnClickListener(v -> openTikTok());
        play.setOnClickListener(v -> togglePlay());
        ImageButton previous = binding.playerPrevious;
        ImageButton next = binding.playerNext;
        previous.setOnTouchListener((v, event) -> handleHoldSeek(event, -1));
        next.setOnTouchListener((v, event) -> handleHoldSeek(event, 1));
        mute.setOnClickListener(v -> viewModel.toggleMute());
        repeat.setOnClickListener(v -> viewModel.cycleRepeat());

        String url = getIntent().getStringExtra(EXTRA_URL);
        MediaItem mediaItem = (MediaItem) getIntent().getSerializableExtra(EXTRA_ITEM);
        boolean batch = getIntent().getBooleanExtra(EXTRA_BATCH, false);
        viewModel.getState().observe(this, this::render);
        viewModel.initialize(new PlayerArgs(
                url,
                getIntent().getStringExtra(EXTRA_TITLE),
                getIntent().getStringExtra(EXTRA_FILE_NAME),
                mediaItem,
                batch,
                getIntent().getIntExtra(EXTRA_REPEAT, settings.getPlaybackRepeat())));
        syncEmbedProgress();

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && durationMs > 0) {
                    position.setText(formatTime(durationMs * progress / 1000L));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                if (durationMs > 0) video.seekTo((int) (durationMs * bar.getProgress() / 1000L));
                updateProgress();
            }
        });

        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        if (url.startsWith("http://")) url = "https://" + url.substring(7);
        binding.playerMore.setTag(url);
        Uri uri = Uri.parse(url);
        videoUri = uri;
        Timber.tag("Player").i("Open video scheme=%s host=%s local=%s",
                uri.getScheme(), uri.getHost(), isLocal(uri));
        try {
            loading.setVisibility(View.VISIBLE);
            if ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) {
                video.setVideoURI(uri);
            } else {
                setRemoteVideo(uri, true);
            }
            video.setOnPreparedListener(mp -> {
                try {
                    mediaPlayer = mp;
                    prepared = true;
                    playRequested = true;
                    durationMs = Math.max(0, video.getDuration());
                    duration.setText(formatTime(durationMs));
                    loading.setVisibility(View.GONE);
                    if (viewModel.isMuted()) mp.setVolume(0f, 0f);
                    applySpeed();
                    if (resumed) {
                        video.start();
                        startTicker();
                    }
                    updatePlayIcon();
                    updateCaption(0);
                    int saved = settings.getPlaybackPosition(videoUri.toString());
                    if (saved > 0 && durationMs > saved + 1000) {
                        video.seekTo(saved);
                        position.setText(formatTime(saved));
                        updateCaption(saved);
                    }
                    Timber.tag("Player").i("Video prepared duration=%d", durationMs);
                } catch (Exception e) {
                    prepared = false;
                    playRequested = false;
                    loading.setVisibility(View.GONE);
                    updatePlayIcon();
                    Timber.tag("Player").w(e, "Video prepared handler failed");
                    Ui.toast(this, R.string.player_error);
                }
            });
            video.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    loading.setVisibility(View.VISIBLE);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                        || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    loading.setVisibility(View.GONE);
                }
                return false;
            });
            video.setOnCompletionListener(mp -> {
                int mode = viewModel.getRepeatMode();
                if (mode == PlayerViewModel.REPEAT_ONE) {
                    playRequested = true;
                    video.seekTo(0);
                    video.start();
                    startTicker();
                } else if (mode == PlayerViewModel.REPEAT_ALL) {
                    playRequested = true;
                    if (!viewModel.openNext(true)) {
                        video.seekTo(0);
                        video.start();
                        startTicker();
                    }
                } else {
                    playRequested = false;
                    stopTicker();
                    updatePlayIcon();
                    updateProgress();
                    savePlaybackPosition();
                }
            });
            video.setOnErrorListener((mp, what, extra) -> {
                prepared = false;
                playRequested = false;
                stopTicker();
                updatePlayIcon();
                Timber.tag("Player").w("VideoView error what=%d extra=%d scheme=%s host=%s",
                        what, extra, uri.getScheme(), uri.getHost());
                if (!headerRetry && videoUri != null && !isLocal(videoUri)) {
                    headerRetry = true;
                    loading.setVisibility(View.VISIBLE);
                    try {
                        setRemoteVideo(videoUri, false);
                        return true;
                    } catch (Exception retryError) {
                        Timber.tag("Player").w(retryError, "VideoView retry without headers failed");
                    }
                }
                loading.setVisibility(View.GONE);
                Ui.toast(this, R.string.player_error);
                return true;
            });
        } catch (Exception e) {
            loading.setVisibility(View.GONE);
            Timber.tag("Player").w(e, "VideoView setup failed scheme=%s host=%s",
                    uri.getScheme(), uri.getHost());
            Ui.toast(this, R.string.player_error);
        }
    }

    private void setRemoteVideo(Uri uri, boolean withHeaders) {
        if (withHeaders && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", Http.MOBILE_UA);
            headers.put("Referer", "https://www.tiktok.com/");
            headers.put("Cookie", "tt_webid_v2=" + LinkUtils.randomWid());
            video.setVideoURI(uri, headers);
        } else {
            video.setVideoURI(uri);
        }
    }

    private static boolean isLocal(Uri uri) {
        String scheme = uri.getScheme();
        return "content".equals(scheme) || "file".equals(scheme);
    }

    private void render(PlayerUiState state) {
        if (state == null) return;
        if (!TextUtils.equals(binding.playerTitle.getText(), state.title)) {
            binding.playerTitle.setText(state.title);
        }
        binding.playerStory.setVisibility(state.story ? View.VISIBLE : View.GONE);
        boolean local = viewModel.getLocalEntry() != null;
        binding.playerMore.setVisibility(local ? View.VISIBLE : View.GONE);
        binding.playerDownload.setVisibility(local ? View.GONE : View.VISIBLE);
        meta.bind(state.author, state.uploadedAt, state.caption, state.hashtags);
        if (lastRepeatMode != state.repeatMode) {
            lastRepeatMode = state.repeatMode;
            settings.setPlaybackRepeat(state.repeatMode);
            updateRepeatTint(state.repeatMode);
        }
        if (Math.abs(lastSpeed - state.speed) > 0.001f) {
            lastSpeed = state.speed;
            speedView.setText(state.speedLabel);
            applySpeed();
        }
        if (lastMuted != state.muted) {
            lastMuted = state.muted;
            applyMute();
            mute.setImageResource(state.muted ? R.drawable.ic_mute : R.drawable.ic_volume);
            mute.setContentDescription(getString(
                    state.muted ? R.string.unmute : R.string.mute));
        }
        if (state.toastNonce > lastToastNonce) {
            lastToastNonce = state.toastNonce;
            if (state.toastRes != 0) Ui.toast(this, state.toastRes);
        }
        if (state.openNonce > lastOpenNonce) {
            lastOpenNonce = state.openNonce;
            if (state.openEntry != null) {
                openEntry(state.openEntry);
                return;
            }
        }
        if (prepared) updateCaption(Math.max(0, video.getCurrentPosition()));
        syncEmbedProgress();
    }

    private void applySpeed() {
        if (mediaPlayer == null) return;
        boolean playing = video.isPlaying();
        try {
            mediaPlayer.setPlaybackParams(
                    mediaPlayer.getPlaybackParams().setSpeed(viewModel.getSpeed()));
            if (!playing) video.pause();
        } catch (Exception ignored) {
        }
    }

    private void applyMute() {
        if (mediaPlayer == null) return;
        boolean muted = viewModel.isMuted();
        mediaPlayer.setVolume(muted ? 0f : 1f, muted ? 0f : 1f);
    }

    private void updateRepeatTint(int repeatMode) {
        repeat.setImageResource(repeatMode == PlayerViewModel.REPEAT_ONE
                ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        int color = getColor(repeatMode == PlayerViewModel.REPEAT_OFF
                ? R.color.text_faint : R.color.primary);
        repeat.setColorFilter(color);
        repeat.setContentDescription(getString(
                repeatMode == PlayerViewModel.REPEAT_OFF ? R.string.repeat_off
                        : repeatMode == PlayerViewModel.REPEAT_ALL
                        ? R.string.repeat_all : R.string.repeat_one));
    }

    private boolean applyHoldSpeed(float rate) {
        if (mediaPlayer == null) return false;
        try {
            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(rate));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean showsEmbedProgress() {
        if (!CaptionEmbedService.isRunning() || viewModel == null) return false;
        DownloadEntry entry = viewModel.getLocalEntry();
        return entry != null && entry.rowId != 0L
                && entry.rowId == CaptionEmbedService.rowId();
    }

    private void syncEmbedProgress() {
        if (embedProgress == null) return;
        if (showsEmbedProgress()) {
            embedProgress.setProgress(CaptionEmbedService.percent());
            embedProgress.setVisibility(View.VISIBLE);
            embedProgress.bringToFront();
        } else {
            embedProgress.setVisibility(View.GONE);
        }
    }

    private void updateCaption(int current) {
        if (viewModel == null || captionView == null) return;
        String text = viewModel.captionFor(current);
        boolean visible = !text.isEmpty();
        if (text.equals(lastCaptionText)
                && captionView.getVisibility() == (visible ? View.VISIBLE : View.GONE)) return;
        lastCaptionText = text;
        captionView.setText(text);
        captionView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void openTikTok() {
        String link = viewModel.getDownloadLink();
        if (MediaItem.notEmpty(link)) Ui.openUrl(this, link);
        else Ui.toast(this, R.string.share_link_unavailable);
    }

    private void showMore(View anchor) {
        DownloadEntry entry = viewModel.getLocalEntry();
        PopupMenu menu = new PopupMenu(this, anchor);
        int openWithId = 1;
        int folderId = 2;
        int infoId = 3;
        int shareId = 4;
        int embedId = 5;
        int showCaptionsId = 6;
        int downloadId = 7;
        int deleteId = 8;
        int cancelEmbedId = 9;
        String link = viewModel.getDownloadLink();
        MediaItem downloadItem = viewModel.downloadItem();
        boolean canDownload = (downloadItem != null
                && (downloadItem.hasContent() || downloadItem.hasMusic()
                || downloadItem.hasCover())) || !link.isEmpty();
        if (entry != null) {
            menu.getMenu().add(0, openWithId, 0, R.string.open_with);
            menu.getMenu().add(0, folderId, 1, R.string.open_folder);
        }
        if (entry != null) {
            menu.getMenu().add(0, infoId, 2, R.string.media_info);
            menu.getMenu().add(0, shareId, 3, R.string.share);
            if (DownloadEntry.TYPE_VIDEO.equals(entry.type)
                    && CaptionBuilder.hasSidecar(this, entry)
                    && !Settings.CAPTION_EMBED.equals(entry.captionMode)) {
                android.view.MenuItem embedItem = menu.getMenu().add(0, embedId, 4,
                        R.string.embed_caption);
                if (CaptionEmbedService.isRunning()) {
                    embedItem.setTitle(R.string.embedding_caption);
                    embedItem.setEnabled(false);
                }
            }
            if (CaptionBuilder.hasSidecar(this, entry)) {
                menu.getMenu().add(0, showCaptionsId, 5, R.string.show_captions)
                        .setCheckable(true)
                        .setChecked(viewModel.isCaptionsVisible());
            }
        }
        if (canDownload) {
            menu.getMenu().add(0, downloadId, 6, R.string.download_options);
        }
        if (entry != null) {
            menu.getMenu().add(1, deleteId, 7, R.string.delete);
            if (CaptionEmbedService.isRunning()) {
                menu.getMenu().add(1, cancelEmbedId, 8, R.string.cancel_embedding);
            }
        }
        MenuDividers.enable(menu.getMenu());
        final DownloadEntry target = entry;
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == downloadId) {
                showDownloadOptions();
                return true;
            }
            if (item.getItemId() == cancelEmbedId) {
                cancelCaptionEmbedding();
                return true;
            }
            if (target == null) return false;
            if (item.getItemId() == infoId) {
                DownloadMenu.showInfo(this, target);
                return true;
            }
            if (item.getItemId() == folderId) {
                Storage.openFolder(this, target);
                return true;
            }
            if (item.getItemId() == openWithId) {
                Storage.openWith(this, target);
                return true;
            }
            if (item.getItemId() == shareId) {
                Storage.share(this, target);
                return true;
            }
            if (item.getItemId() == embedId) {
                embedCaption(target);
                return true;
            }
            if (item.getItemId() == showCaptionsId) {
                viewModel.toggleCaptions();
                updateCaption(Math.max(0, video.getCurrentPosition()));
                return true;
            }
            if (item.getItemId() == deleteId) {
                cancelEmbeddingFor(target.rowId);
                DeleteDialogs.show(this, false, () -> {
                    Storage.delete(this, target.localUri);
                    new DownloadDb(this).delete(target.rowId);
                    finish();
                }, () -> {
                    new DownloadDb(this).delete(target.rowId);
                    finish();
                });
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showDownloadOptions() {
        MediaItem item = viewModel.downloadItem();
        String link = viewModel.getDownloadLink();
        if (viewModel.getLocalEntry() != null && MediaItem.notEmpty(link)) {
            resolveDownloadOptions(link, item);
            return;
        }
        if (item != null) {
            showDownloadOptions(item);
            return;
        }
        if (MediaItem.notEmpty(link)) {
            resolveDownloadOptions(link, null);
            return;
        }
        Ui.toast(this, R.string.no_media);
    }

    private void resolveDownloadOptions(String link, MediaItem fallback) {
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
                } else if (fallback != null) {
                    showDownloadOptions(fallback);
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
                        toastQueued(DownloadController.enqueue(PlayerActivity.this,
                                item, kind, preferHd, captions, captionMode));
                    }

                    @Override
                    public void onEnqueueImages(List<String> images, boolean withMusic) {
                        toastQueued(DownloadController.enqueueImages(PlayerActivity.this,
                                item, images, withMusic));
                    }

                    @Override
                    public void onEnqueueSlideshow(List<String> images) {
                        toastQueued(DownloadController.enqueueSlideshow(
                                PlayerActivity.this, item));
                    }
                });
    }

    private void toastQueued(int count) {
        if (count > 0) Ui.downloadStarted(this, getString(R.string.download_queued_count, count));
        else Ui.toast(this, R.string.no_media);
    }

    private void embedCaption(DownloadEntry entry) {
        if (CaptionEmbedService.isRunning()) {
            Ui.toast(this, R.string.caption_embed_busy);
            return;
        }
        if (entry == null || !Storage.exists(this, entry)) {
            Ui.toast(this, R.string.caption_embed_failed);
            return;
        }
        if (!CaptionEmbedService.start(this, entry.rowId)) {
            Ui.toast(this, R.string.caption_embed_failed);
            return;
        }
        embedProgress.setProgress(0);
        embedProgress.setVisibility(View.VISIBLE);
        embedProgress.bringToFront();
    }

    private void cancelCaptionEmbedding() {
        CaptionEmbedService.cancel(this);
    }

    public static void cancelEmbeddingFor(long rowId) {
        CaptionEmbedService.cancelFor(rowId);
    }

    private void togglePlay() {
        if (!prepared) return;
        if (playRequested) {
            video.pause();
            playRequested = false;
            stopTicker();
        } else {
            video.start();
            playRequested = true;
            startTicker();
        }
        updatePlayIcon();
    }

    private boolean handleHoldSeek(MotionEvent event, int dir) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!prepared || durationMs <= 0) return false;
            handler.removeCallbacks(holdStart);
            holdDir = dir;
            holdTarget = Math.max(0, video.getCurrentPosition());
            holdStartPos = holdTarget;
            holdActive = false;
            holdWasPlaying = video.isPlaying();
            handler.postDelayed(holdStart, 300L);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            int held = holdDir;
            boolean active = holdActive;
            boolean speedMode = holdSpeedMode;
            handler.removeCallbacks(holdStart);
            stopHold();
            if (active) {
                if (speedMode) {
                    applySpeed();
                    if (!holdWasPlaying && prepared) {
                        video.pause();
                        playRequested = false;
                        stopTicker();
                    } else if (prepared) {
                        playRequested = true;
                        startTicker();
                    }
                    updatePlayIcon();
                } else if (holdWasPlaying && prepared) {
                    video.start();
                    playRequested = true;
                    startTicker();
                    updatePlayIcon();
                } else if (prepared) {
                    video.pause();
                    playRequested = false;
                    stopTicker();
                    updatePlayIcon();
                }
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                navigate(held < 0 ? -1 : 1);
            }
            return true;
        }
        return true;
    }

    private void stopHold() {
        holdActive = false;
        holdSpeedMode = false;
        holdDir = 0;
        handler.removeCallbacks(holdStart);
        handler.removeCallbacks(holdTick);
    }

    private void updateHoldSeek() {
        if (!holdActive || !prepared || durationMs <= 0) return;
        long elapsed = now() - holdDownAt;
        int target;
        if (holdDir < 0) {
            if (elapsed - lastHoldSeekAt < 500L) return;
            lastHoldSeekAt = elapsed;
            target = holdStartPos - (int) Math.min((long) durationMs,
                    (elapsed / 1000L) * 2000L);
        } else {
            int delta = (int) Math.min((long) durationMs, elapsed * 4L);
            target = holdStartPos + delta;
        }
        if (target < 0) target = 0;
        if (target > durationMs) target = durationMs;
        if (target == holdTarget) return;
        holdTarget = target;
        video.seekTo(target);
        position.setText(formatTime(target));
    }

    private void navigate(int direction) {
        viewModel.navigate(direction);
    }

    private void openEntry(DownloadEntry entry) {
        if (entry == null) return;
        playRequested = false;
        if (video != null) video.pause();
        stopTicker();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(EXTRA_URL, entry.localUri);
        intent.putExtra(EXTRA_TITLE, entry.title);
        intent.putExtra(EXTRA_REPEAT, viewModel.getRepeatMode());
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (edgeSwipe != null && edgeSwipe.onTouchEvent(event)) return true;
        return super.dispatchTouchEvent(event);
    }

    private void updatePlayIcon() {
        boolean playing = prepared && playRequested;
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(getString(playing ? R.string.pause : R.string.play));
    }

    private void updateProgress() {
        if (!prepared || seeking) return;
        int current = Math.max(0, video.getCurrentPosition());
        if (durationMs > 0) {
            seek.setProgress((int) (current * 1000L / durationMs));
        }
        position.setText(formatTime(current));
        syncEmbedProgress();
        updateCaption(current);
    }

    private void savePlaybackPosition() {
        if (!prepared || videoUri == null) return;
        int current = Math.max(0, video.getCurrentPosition());
        if (durationMs > 0 && current >= durationMs - 750) current = 0;
        settings.setPlaybackPosition(videoUri.toString(), current);
    }

    private String formatTime(long ms) {
        long total = Math.max(0, ms) / 1000L;
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    @Override
    protected void onPause() {
        resumed = false;
        savePlaybackPosition();
        if (video != null) video.pause();
        stopTicker();
        updatePlayIcon();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        syncEmbedProgress();
        if (prepared && playRequested) {
            video.start();
            startTicker();
            updatePlayIcon();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        prepared = false;
        playRequested = false;
        resumed = false;
        if (video != null) {
            try {
                video.stopPlayback();
            } catch (Exception ignored) {
            }
        }
        mediaPlayer = null;
        super.onDestroy();
    }
}
