package com.theuncoaffair.fishpipe.ui.photoplayer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.net.Http;
import com.theuncoaffair.fishpipe.net.LinkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class PhotoPlayerViewModel extends ViewModel {
    private final Context context;
    private final MutableLiveData<PhotoPlayerUiState> state = new MutableLiveData<>();

    private final List<String> images = new ArrayList<>();
    @Nullable
    private MediaItem item;
    private String title = "";
    private String currentUrl = "";
    private boolean story;
    private int index;
    private int toastRes;
    private int toastNonce;
    private int finishNonce;
    private boolean initialized;

    @Nullable
    private MediaPlayer audio;
    private boolean audioPrepared;
    private boolean resumed;

    @Inject
    public PhotoPlayerViewModel(@ApplicationContext Context context) {
        this.context = context;
        publish();
    }

    public LiveData<PhotoPlayerUiState> getState() {
        return state;
    }

    public void initialize(@Nullable MediaItem mediaItem) {
        if (initialized) return;
        initialized = true;
        item = mediaItem;
        if (mediaItem == null || mediaItem.images == null) {
            finishNonce++;
            publish();
            return;
        }
        for (String url : mediaItem.images) {
            if (MediaItem.notEmpty(url)) images.add(url);
        }
        if (images.isEmpty()) {
            finishNonce++;
            publish();
            return;
        }
        title = MediaItem.notEmpty(mediaItem.title)
                ? mediaItem.title : context.getString(R.string.app_name);
        story = mediaItem.story;
        index = 0;
        currentUrl = images.get(0);
        publish();
        prepareAudio();
    }

    public boolean slide(int direction) {
        if (images.isEmpty()) return false;
        int next = index + direction;
        if (next < 0 || next >= images.size()) {
            toastRes = direction < 0 ? R.string.no_previous_media : R.string.no_next_media;
            toastNonce++;
            publish();
            return false;
        }
        index = next;
        currentUrl = images.get(index);
        publish();
        return true;
    }

    public void onResume() {
        resumed = true;
        if (audioPrepared && audio != null && !audio.isPlaying()) audio.start();
    }

    public void onPause() {
        resumed = false;
        if (audio != null && audio.isPlaying()) audio.pause();
    }

    private void prepareAudio() {
        if (item == null || !MediaItem.notEmpty(item.musicUrl)) return;
        try {
            audio = new MediaPlayer();
            audio.setAudioStreamType(AudioManager.STREAM_MUSIC);
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", Http.MOBILE_UA);
            headers.put("Referer", "https://www.tiktok.com/");
            headers.put("Cookie", "tt_webid_v2=" + LinkUtils.randomWid());
            audio.setDataSource(context, Uri.parse(item.musicUrl), headers);
            audio.setLooping(true);
            audio.setOnPreparedListener(mp -> {
                audioPrepared = true;
                if (resumed) mp.start();
            });
            audio.setOnErrorListener((mp, what, extra) -> {
                audioPrepared = false;
                releaseAudio();
                return true;
            });
            audio.prepareAsync();
        } catch (Exception ignored) {
            releaseAudio();
        }
    }

    private void releaseAudio() {
        if (audio == null) return;
        try {
            audio.reset();
        } catch (Exception ignored) {
        }
        try {
            audio.release();
        } catch (Exception ignored) {
        }
        audio = null;
        audioPrepared = false;
    }

    private void publish() {
        state.setValue(new PhotoPlayerUiState(
                item,
                title,
                currentUrl,
                story,
                index,
                images.size(),
                toastRes,
                toastNonce,
                finishNonce));
    }

    @Override
    protected void onCleared() {
        releaseAudio();
        super.onCleared();
    }
}
