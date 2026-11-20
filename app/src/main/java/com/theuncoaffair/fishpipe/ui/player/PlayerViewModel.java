package com.theuncoaffair.fishpipe.ui.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.download.CaptionBuilder;
import com.theuncoaffair.fishpipe.download.Storage;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class PlayerViewModel extends ViewModel {
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.15f, 2.5f, 3f};
    private static final String[] SPEED_LABELS =
            {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "2.15x", "2.5x", "3x"};

    private final Context context;
    private final ExecutorService io;
    @Nullable
    private Handler handler;
    private final MutableLiveData<PlayerUiState> state =
            new MutableLiveData<>(PlayerUiState.idle());

    private boolean initialized;
    private String url = "";
    @Nullable
    private MediaItem mediaItem;
    @Nullable
    private DownloadEntry localEntry;
    private List<DownloadEntry> queue = Collections.emptyList();
    private int queueIndex = -1;

    private String title = "";
    private String shareUrl = "";
    private String author = "";
    private String caption = "";
    private String hashtags = "";
    private boolean story;
    private long uploadedAt;
    private int repeatMode = REPEAT_OFF;
    private float speed = 1f;
    private boolean muted;
    private boolean captionsVisible = true;
    private List<CaptionBuilder.Cue> captionCues = Collections.emptyList();
    private int captionRequest;

    private int toastRes;
    private int toastNonce;
    @Nullable
    private DownloadEntry openEntry;
    private int openNonce;

    @Inject
    public PlayerViewModel(@ApplicationContext Context context,
                           @Named(AppModule.IO) ExecutorService io) {
        this.context = context;
        this.io = io;
    }

    @NonNull
    public LiveData<PlayerUiState> getState() {
        return state;
    }

    public void initialize(@NonNull PlayerArgs args) {
        if (initialized) return;
        initialized = true;
        url = args.url;
        mediaItem = args.mediaItem;
        repeatMode = Math.max(REPEAT_OFF, Math.min(REPEAT_ONE, args.repeatMode));

        if (mediaItem == null && !url.isEmpty()) {
            try {
                localEntry = new DownloadDb(context).findByLocalUri(url);
            } catch (Exception ignored) {
                localEntry = null;
            }
        }
        story = (mediaItem != null && mediaItem.story)
                || (localEntry != null && localEntry.story);
        title = resolveTitle(args);
        resolveMeta();
        shareUrl = mediaItem != null && MediaItem.notEmpty(mediaItem.pageUrl)
                ? mediaItem.pageUrl : url;
        loadQueue();
        loadCaptionCues();
        publish();
    }

    @Nullable
    public DownloadEntry getLocalEntry() {
        return localEntry;
    }

    @NonNull
    public String getShareUrl() {
        return shareUrl;
    }

    @NonNull
    public String getDownloadLink() {
        if (mediaItem != null && MediaItem.notEmpty(mediaItem.pageUrl)) {
            return mediaItem.pageUrl;
        }
        if (localEntry != null && MediaItem.notEmpty(localEntry.pageUrl)) {
            return localEntry.pageUrl;
        }
        if (MediaItem.notEmpty(url)
                && !url.startsWith("content:") && !url.startsWith("file:")
                && !url.startsWith("/")) {
            return url;
        }
        return "";
    }

    @Nullable
    public MediaItem downloadItem() {
        if (mediaItem != null) return mediaItem;
        if (localEntry == null || !MediaItem.notEmpty(localEntry.remoteUrl)) return null;
        MediaItem item = new MediaItem();
        item.id = localEntry.mediaId;
        item.title = localEntry.title;
        item.author = localEntry.author;
        item.authorAvatar = localEntry.authorAvatar;
        item.pageUrl = localEntry.pageUrl;
        item.coverUrl = localEntry.thumbUrl;
        item.musicCoverUrl = localEntry.thumbUrl;
        item.createTime = localEntry.uploadedAt / 1000L;
        if (DownloadEntry.TYPE_MUSIC.equals(localEntry.type)) {
            item.musicUrl = localEntry.remoteUrl;
            item.musicTitle = localEntry.title;
        } else if (DownloadEntry.TYPE_IMAGE.equals(localEntry.type)) {
            item.images.add(localEntry.remoteUrl);
        } else {
            item.videoUrl = localEntry.remoteUrl;
        }
        return item;
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
        publish();
    }

    public float getSpeed() {
        return speed;
    }

    public void cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        toastRes = repeatMode == REPEAT_OFF ? R.string.repeat_off
                : repeatMode == REPEAT_ALL ? R.string.repeat_all : R.string.repeat_one;
        toastNonce++;
        publish();
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public void toggleMute() {
        muted = !muted;
        publish();
    }

    public boolean isMuted() {
        return muted;
    }

    public void toggleCaptions() {
        captionsVisible = !captionsVisible;
        publish();
    }

    public boolean isCaptionsVisible() {
        return captionsVisible;
    }

    @NonNull
    public String captionFor(int positionMs) {
        if (!captionsVisible) return "";
        for (CaptionBuilder.Cue cue : captionCues) {
            if (positionMs >= cue.startMs && positionMs < cue.endMs) {
                return cue.text;
            }
        }
        return "";
    }

    public void previous() {
        if (queueIndex > 0) {
            navigateTo(queue.get(queueIndex - 1));
        } else {
            toast(R.string.no_previous_media);
        }
    }

    public void next() {
        if (!openNext(false)) toast(R.string.no_next_media);
    }

    public boolean openNext(boolean wrap) {
        if (queue.isEmpty() || queueIndex < 0) return false;
        int next = queueIndex + 1;
        if (next >= queue.size()) {
            if (!wrap) return false;
            next = 0;
        }
        if (next == queueIndex) return false;
        navigateTo(queue.get(next));
        return true;
    }

    public void navigate(int direction) {
        if (direction < 0) previous();
        else next();
    }

    private void navigateTo(@NonNull DownloadEntry entry) {
        openEntry = entry;
        openNonce++;
        publish();
    }

    private void toast(int res) {
        toastRes = res;
        toastNonce++;
        publish();
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

    private void loadCaptionCues() {
        if (localEntry == null) return;
        DownloadEntry entry = localEntry;
        int request = ++captionRequest;
        io.execute(() -> {
            List<CaptionBuilder.Cue> cues = CaptionBuilder.loadSidecar(context, entry);
            mainHandler().post(() -> {
                if (request != captionRequest) return;
                captionCues = cues;
                publish();
            });
        });
    }

    @NonNull
    private String resolveTitle(@NonNull PlayerArgs args) {
        if (localEntry != null) {
            String fileName = Storage.displayName(context, localEntry.localUri);
            if (MediaItem.notEmpty(fileName)) return fileName;
            if (MediaItem.notEmpty(localEntry.title)) return localEntry.title;
            return context.getString(R.string.app_name);
        }
        return context.getString(R.string.app_name);
    }

    private void resolveMeta() {
        author = "";
        uploadedAt = 0L;
        caption = "";
        hashtags = "";
        if (mediaItem != null) {
            author = mediaItem.author;
            if (mediaItem.createTime > 0) uploadedAt = mediaItem.createTime * 1000L;
            caption = mediaItem.captionFromTitle();
            hashtags = mediaItem.hashtagsFromTitle();
        }
        if (localEntry != null) {
            if (!MediaItem.notEmpty(author)) author = localEntry.author;
            if (uploadedAt <= 0) uploadedAt = localEntry.uploadedAt;
            if (!MediaItem.notEmpty(caption)) {
                caption = MediaItem.captionForDisplay(MediaItem.notEmpty(localEntry.caption)
                        ? localEntry.caption : localEntry.title);
            }
            if (!MediaItem.notEmpty(hashtags)) {
                hashtags = MediaItem.notEmpty(localEntry.hashtags)
                        ? localEntry.hashtags : MediaItem.hashtagsOf(localEntry.title);
            }
        }
    }

    private void publish() {
        state.setValue(new PlayerUiState(
                title,
                shareUrl,
                author,
                caption,
                hashtags,
                story,
                uploadedAt,
                repeatMode,
                speed,
                speedLabel(),
                muted,
                captionsVisible,
                toastRes,
                toastNonce,
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

    private Handler mainHandler() {
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        return handler;
    }

    @Override
    protected void onCleared() {
        captionRequest++;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        super.onCleared();
    }
}
