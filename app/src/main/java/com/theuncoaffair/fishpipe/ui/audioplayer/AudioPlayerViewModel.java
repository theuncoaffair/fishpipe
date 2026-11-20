package com.theuncoaffair.fishpipe.ui.audioplayer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.core.AppClock;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.download.CaptionBuilder;
import com.theuncoaffair.fishpipe.download.Storage;
import com.theuncoaffair.fishpipe.net.Http;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class AudioPlayerViewModel extends ViewModel {
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.15f, 2.5f, 3f};
    private static final String[] SPEED_LABELS =
            {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "2.15x", "2.5x", "3x"};

    private final Context context;
    private final ExecutorService io;
    private final AppClock clock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<AudioPlayerUiState> state =
            new MutableLiveData<>(AudioPlayerUiState.idle());

    @Nullable
    private MediaPlayer player;
    @Nullable
    private AudioManager audioManager;
    @Nullable
    private AudioFocusRequest focusRequest;
    private boolean focusHeld;
    private boolean initialized;
    private boolean resumed;
    private boolean prepared;
    private boolean playing;
    private boolean loading;
    private boolean playWhenResumed;
    private boolean resumeOnFocus;
    private int durationMs;
    private int positionMs;
    private float speed = 1f;
    private int repeatMode = REPEAT_OFF;

    private String audioUrl = "";
    private String audioTitle = "";
    private String audioAuthor = "";
    private String audioCover = "";
    private String pageUrl = "";
    private String fileName = "";
    private boolean story;
    private long uploadedAt;

    @Nullable
    private DownloadEntry localEntry;
    private List<DownloadEntry> queue = Collections.emptyList();
    private int queueIndex = -1;

    private List<CaptionBuilder.Cue> captionCues = Collections.emptyList();
    private String captionText = "";
    private int captionRequest;

    private int toastRes;
    private int toastNonce;
    private int finishNonce;
    @Nullable
    private DownloadEntry openEntry;
    private int openNonce;

    private boolean holdActive;
    private boolean holdSpeedMode;
    private boolean holdWasPlaying;
    private int holdDir;
    private int holdTarget;
    private int holdStartPos;
    private long holdDownAt;
    private float holdRate = 2f;

    private final Runnable holdTick = new Runnable() {
        @Override
        public void run() {
            if (!holdActive) return;
            updateHoldSeek();
            handler.postDelayed(this, 80L);
        }
    };

    private final Runnable holdStart;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (player == null || !prepared) return;
            positionMs = Math.max(0, player.getCurrentPosition());
            updateCaption(positionMs);
            publish();
            handler.postDelayed(this, 500L);
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            resumeOnFocus = player != null && player.isPlaying();
            if (resumeOnFocus) pause();
        } else if (change == AudioManager.AUDIOFOCUS_GAIN && resumeOnFocus) {
            resumeOnFocus = false;
            if (resumed) start();
            else playWhenResumed = true;
        }
    };

    @Inject
    public AudioPlayerViewModel(@ApplicationContext Context context,
                                @Named(AppModule.IO) ExecutorService io,
                                AppClock clock) {
        this.context = context;
        this.io = io;
        this.clock = clock;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.holdStart = this::startHold;
    }

    @NonNull
    public LiveData<AudioPlayerUiState> getState() {
        return state;
    }

    @Nullable
    public DownloadEntry getLocalEntry() {
        return localEntry;
    }

    @NonNull
    public String getPageUrl() {
        return pageUrl;
    }

    @NonNull
    public String getAudioUrl() {
        return audioUrl;
    }

    public void initialize(@NonNull AudioPlayerArgs args) {
        if (initialized) return;
        initialized = true;
        audioUrl = args.url;
        audioTitle = args.title;
        audioAuthor = args.author;
        audioCover = args.cover;
        pageUrl = args.pageUrl;
        fileName = args.fileName;
        repeatMode = Math.max(REPEAT_OFF, Math.min(REPEAT_ONE, args.repeatMode));

        if (audioUrl.isEmpty()) {
            finishNonce++;
            publish();
            return;
        }

        try {
            localEntry = new DownloadDb(context).findByLocalUri(audioUrl);
        } catch (Exception ignored) {
            localEntry = null;
        }
        if (localEntry != null) {
            if (pageUrl.isEmpty()) pageUrl = localEntry.pageUrl;
            story = localEntry.story;
            uploadedAt = localEntry.uploadedAt;
        }
        if (fileName.isEmpty() && localEntry != null) {
            fileName = Storage.displayName(context, localEntry.localUri);
        }
        if (fileName.trim().isEmpty()) fileName = context.getString(R.string.now_playing);
        audioTitle = audioTitle.trim().isEmpty()
                ? context.getString(R.string.app_name) : audioTitle;

        loadQueue();
        loadCaptionCues();
        publish();
        prepare(Uri.parse(audioUrl));
    }

    public void toggle() {
        if (player != null && prepared && player.isPlaying()) pause();
        else start();
    }

    public void seekToPercent(int percent) {
        if (player == null || !prepared || durationMs <= 0) return;
        int target = (int) ((long) durationMs
                * Math.max(0, Math.min(1000, percent)) / 1000L);
        try {
            player.seekTo(target);
        } catch (Exception ignored) {
            return;
        }
        positionMs = target;
        updateCaption(target);
        publish();
    }

    public void cycleSpeed() {
        int index = 0;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Math.abs(SPEEDS[i] - speed) < 0.001f) {
                index = (i + 1) % SPEEDS.length;
                break;
            }
        }
        speed = SPEEDS[index];
        applySpeed();
        publish();
    }

    public void cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        toastRes = repeatMode == REPEAT_OFF ? R.string.repeat_off
                : repeatMode == REPEAT_ALL ? R.string.repeat_all : R.string.repeat_one;
        toastNonce++;
        publish();
    }

    public void previous() {
        if (queueIndex > 0) {
            navigateTo(queue.get(queueIndex - 1));
        } else {
            toast(R.string.no_previous_media);
        }
    }

    public void next() {
        DownloadEntry entry = nextEntry(false);
        if (entry != null) navigateTo(entry);
        else toast(R.string.no_next_media);
    }

    public void navigate(int direction) {
        if (direction < 0) previous();
        else next();
    }

    private void startHold() {
        if (holdDir == 0 || player == null || !prepared) return;
        holdActive = true;
        holdStartPos = holdTarget;
        holdDownAt = clock.nowMillis();
        holdRate = Math.min(8f, Math.max(2f, speed * 4f));
        holdSpeedMode = holdDir > 0 && applyHoldSpeed(holdRate);
        if (holdSpeedMode) {
            if (!player.isPlaying()) start();
        } else {
            if (holdWasPlaying) pause();
            else start();
            handler.post(holdTick);
        }
        publish();
    }

    public boolean onHoldDown(int direction) {
        if (player == null || !prepared || durationMs <= 0) return false;
        handler.removeCallbacks(holdStart);
        holdDir = direction;
        holdTarget = Math.max(0, player.getCurrentPosition());
        holdStartPos = holdTarget;
        holdActive = false;
        holdSpeedMode = false;
        holdWasPlaying = player.isPlaying();
        holdDownAt = clock.nowMillis();
        handler.postDelayed(holdStart, 300L);
        return true;
    }

    public void onHoldEnd(boolean cancelled) {
        int held = holdDir;
        boolean active = holdActive;
        boolean speedMode = holdSpeedMode;
        handler.removeCallbacks(holdStart);
        stopHold();
        if (active) {
            if (speedMode) {
                applySpeed();
                if (!holdWasPlaying && prepared) pause();
            } else if (holdWasPlaying && prepared) {
                start();
            } else if (prepared) {
                pause();
            }
        } else if (!cancelled && held != 0) {
            if (held < 0) previous();
            else next();
        }
    }

    public void onActivityResume() {
        resumed = true;
        if (prepared && playWhenResumed) {
            playWhenResumed = false;
            start();
        } else if (prepared) {
            startTicker();
        }
    }

    public void onActivityPause() {
        resumed = false;
        stopTicker();
        if (player != null && prepared) {
            try {
                if (player.isPlaying()) {
                    playWhenResumed = true;
                    player.pause();
                }
            } catch (Exception ignored) {
            }
            if (playing) {
                playing = false;
                publish();
            }
        }
    }

    @NonNull
    public MediaItem downloadItem() {
        MediaItem item = new MediaItem();
        item.title = localEntry == null ? audioTitle : localEntry.title;
        item.author = localEntry == null ? audioAuthor : localEntry.author;
        item.authorAvatar = localEntry == null ? "" : localEntry.authorAvatar;
        item.pageUrl = localEntry == null ? pageUrl : localEntry.pageUrl;
        item.musicUrl = localEntry == null ? audioUrl : localEntry.remoteUrl;
        item.musicTitle = item.title;
        item.musicCoverUrl = localEntry == null ? audioCover : localEntry.thumbUrl;
        item.coverUrl = item.musicCoverUrl;
        if (localEntry != null) {
            item.id = localEntry.mediaId;
            item.createTime = localEntry.uploadedAt / 1000L;
        }
        return item;
    }

    private void prepare(@NonNull Uri uri) {
        releasePlayer();
        try {
            loading = true;
            publish();
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setOnPreparedListener(this::onPrepared);
            mp.setOnCompletionListener(this::onCompletion);
            mp.setOnInfoListener((mediaPlayer, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    loading = true;
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                        || what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                    loading = false;
                }
                publish();
                return false;
            });
            mp.setOnErrorListener((mediaPlayer, what, extra) -> {
                failPlayback();
                return true;
            });
            if ("content".equals(uri.getScheme()) || "file".equals(uri.getScheme())) {
                mp.setDataSource(context, uri);
            } else {
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", Http.MOBILE_UA);
                headers.put("Referer", "https://www.tiktok.com/");
                mp.setDataSource(context, uri, headers);
            }
            mp.prepareAsync();
        } catch (Exception e) {
            failPlayback();
        }
    }

    private void onPrepared(@NonNull MediaPlayer mp) {
        if (mp != player) return;
        prepared = true;
        durationMs = Math.max(0, mp.getDuration());
        loading = false;
        if (resumed) {
            start();
        } else {
            playWhenResumed = true;
            publish();
        }
        applySpeed();
    }

    private void onCompletion(@NonNull MediaPlayer mp) {
        if (mp != player) return;
        if (repeatMode == REPEAT_ONE) {
            mp.seekTo(0);
            start();
            return;
        }
        if (repeatMode == REPEAT_ALL) {
            DownloadEntry entry = nextEntry(true);
            if (entry != null) {
                stopTicker();
                navigateTo(entry);
                return;
            }
            mp.seekTo(0);
            start();
            return;
        }
        playing = false;
        positionMs = durationMs;
        stopTicker();
        publish();
    }

    private void start() {
        if (player == null || !prepared) return;
        try {
            requestFocus();
            player.start();
            playing = true;
            startTicker();
            publish();
        } catch (Exception e) {
            failPlayback();
        }
    }

    private void pause() {
        if (player == null || !prepared) return;
        stopTicker();
        try {
            if (player.isPlaying()) player.pause();
            playing = false;
            publish();
        } catch (Exception ignored) {
        }
    }

    private void applySpeed() {
        if (player == null || !prepared) return;
        boolean wasPlaying = player.isPlaying();
        try {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
            if (!wasPlaying) player.pause();
            playing = wasPlaying;
            publish();
        } catch (Exception ignored) {
        }
    }

    private boolean applyHoldSpeed(float rate) {
        if (player == null || !prepared) return false;
        try {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(rate));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateHoldSeek() {
        if (!holdActive || player == null || !prepared || durationMs <= 0) return;
        long elapsed = clock.nowMillis() - holdDownAt;
        int delta = (int) Math.min((long) durationMs, elapsed * 4L);
        int target = holdStartPos + holdDir * delta;
        if (target < 0) target = 0;
        if (target > durationMs) target = durationMs;
        if (target == holdTarget) return;
        holdTarget = target;
        player.seekTo(target);
        positionMs = target;
        updateCaption(target);
        publish();
    }

    private void stopHold() {
        holdActive = false;
        holdSpeedMode = false;
        holdDir = 0;
        handler.removeCallbacks(holdStart);
        handler.removeCallbacks(holdTick);
    }

    private void loadQueue() {
        queue = Collections.emptyList();
        queueIndex = -1;
        if (localEntry == null) return;
        try {
            queue = new DownloadDb(context).listForPlayback(localEntry.type);
        } catch (Exception ignored) {
            return;
        }
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).rowId == localEntry.rowId) {
                queueIndex = i;
                break;
            }
        }
    }

    @Nullable
    private DownloadEntry nextEntry(boolean wrap) {
        if (queue.isEmpty() || queueIndex < 0) return null;
        int next = queueIndex + 1;
        if (next >= queue.size()) {
            if (!wrap) return null;
            next = 0;
        }
        if (next == queueIndex) return null;
        return queue.get(next);
    }

    private void navigateTo(@NonNull DownloadEntry entry) {
        openEntry = entry;
        openNonce++;
        publish();
    }

    private void loadCaptionCues() {
        if (localEntry == null) return;
        DownloadEntry entry = localEntry;
        int request = ++captionRequest;
        io.execute(() -> {
            List<CaptionBuilder.Cue> cues = CaptionBuilder.loadSidecar(context, entry);
            handler.post(() -> {
                if (request != captionRequest) return;
                captionCues = cues;
                captionText = "";
                publish();
            });
        });
    }

    private void updateCaption(int current) {
        String text = "";
        for (CaptionBuilder.Cue cue : captionCues) {
            if (current >= cue.startMs && current < cue.endMs) {
                text = cue.text;
                break;
            }
        }
        captionText = text;
    }

    private void requestFocus() {
        if (audioManager == null || focusHeld) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            //noinspection deprecation
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
        focusHeld = true;
    }

    private void abandonFocus() {
        if (audioManager == null || !focusHeld) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
        } else {
            //noinspection deprecation
            audioManager.abandonAudioFocus(focusListener);
        }
        focusHeld = false;
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void stopTicker() {
        handler.removeCallbacks(ticker);
    }

    private void failPlayback() {
        stopTicker();
        loading = false;
        prepared = false;
        playing = false;
        playWhenResumed = false;
        toast(R.string.playback_error);
    }

    private void toast(int res) {
        toastRes = res;
        toastNonce++;
        publish();
    }

    private void releasePlayer() {
        if (player == null) return;
        try {
            player.reset();
        } catch (Exception ignored) {
        }
        try {
            player.release();
        } catch (Exception ignored) {
        }
        player = null;
        prepared = false;
        playing = false;
        loading = false;
    }

    private void publish() {
        state.setValue(new AudioPlayerUiState(
                fileName,
                audioTitle,
                audioAuthor,
                audioCover,
                pageUrl,
                audioUrl,
                story,
                uploadedAt,
                prepared,
                playing,
                loading,
                positionMs,
                durationMs,
                speed,
                speedLabel(),
                repeatMode,
                captionText,
                toastRes,
                toastNonce,
                finishNonce,
                openEntry,
                openNonce));
    }

    @NonNull
    private String speedLabel() {
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Math.abs(SPEEDS[i] - speed) < 0.001f) return SPEED_LABELS[i];
        }
        return SPEED_LABELS[2];
    }

    @Override
    protected void onCleared() {
        captionRequest++;
        handler.removeCallbacksAndMessages(null);
        abandonFocus();
        releasePlayer();
        super.onCleared();
    }
}
