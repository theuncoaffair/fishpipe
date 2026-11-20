package com.theuncoaffair.fishpipe.ui.imageviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.di.AppModule;
import com.theuncoaffair.fishpipe.download.Storage;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class ImageViewerViewModel extends ViewModel {
    private final Context context;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<ImageViewerUiState> state = new MutableLiveData<>();

    private String uri;
    private String extraTitle;
    @Nullable
    private DownloadEntry entry;
    private List<DownloadEntry> queue = new ArrayList<>();
    private int queueIndex = -1;
    private String title = "";
    private boolean story;
    private boolean loading;
    private boolean remote;
    private String remoteUrl;
    private Bitmap bitmap;
    private int finishNonce;

    @Inject
    public ImageViewerViewModel(@ApplicationContext Context context,
                                @Named(AppModule.IO) ExecutorService io) {
        this.context = context;
        this.io = io;
        publish();
    }

    public LiveData<ImageViewerUiState> getState() {
        return state;
    }

    public void initialize(@Nullable String rawUri, @Nullable String rawTitle) {
        if (uri != null) return;
        uri = rawUri == null ? "" : rawUri;
        extraTitle = rawTitle;
        loading = true;
        publish();
        io.execute(this::loadEntry);
    }

    @Nullable
    public DownloadEntry previous() {
        return queueIndex > 0 ? queue.get(queueIndex - 1) : null;
    }

    @Nullable
    public DownloadEntry next() {
        return queueIndex >= 0 && queueIndex + 1 < queue.size()
                ? queue.get(queueIndex + 1) : null;
    }

    public void delete(boolean deleteFile) {
        final DownloadEntry target = entry;
        if (target == null) return;
        io.execute(() -> {
            if (deleteFile) Storage.delete(context, target.localUri);
            new DownloadDb(context).delete(target.rowId);
            main.post(() -> {
                finishNonce++;
                publish();
            });
        });
    }

    private void loadEntry() {
        DownloadEntry found = null;
        List<DownloadEntry> list = new ArrayList<>();
        int index = -1;
        String display = "";
        boolean isStory = false;
        try {
            DownloadDb db = new DownloadDb(context);
            found = db.findByLocalUri(uri);
            if (found != null) {
                list = db.listForPlayback(found.type);
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).rowId == found.rowId) {
                        index = i;
                        break;
                    }
                }
                display = Storage.displayName(context, found.localUri);
                if (!MediaItem.notEmpty(display) && MediaItem.notEmpty(found.title)) {
                    display = found.title;
                }
                isStory = found.story;
            }
        } catch (Exception ignored) {
        }
        if (!MediaItem.notEmpty(display) && MediaItem.notEmpty(extraTitle)) {
            display = extraTitle;
        }
        final DownloadEntry finalEntry = found;
        final List<DownloadEntry> finalQueue = list;
        final int finalIndex = index;
        final String finalDisplay = display == null ? "" : display;
        final boolean finalStory = isStory;
        main.post(() -> {
            entry = finalEntry;
            queue = finalQueue;
            queueIndex = finalIndex;
            title = finalDisplay;
            story = finalStory;
            loadImage();
        });
    }

    private void loadImage() {
        if (uri.trim().isEmpty()) {
            loading = false;
            publish();
            return;
        }
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            remote = true;
            remoteUrl = uri;
            loading = false;
            publish();
            return;
        }
        io.execute(() -> {
            Bitmap decoded = decode(uri);
            main.post(() -> {
                loading = false;
                bitmap = decoded;
                publish();
            });
        });
    }

    @Nullable
    private Bitmap decode(String source) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream probe = open(source)) {
                if (probe == null) return null;
                BitmapFactory.decodeStream(probe, null, bounds);
            }
            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > 2048) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = open(source)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private InputStream open(String source) {
        try {
            Uri parsed = Uri.parse(source);
            if ("content".equals(parsed.getScheme()) || "file".equals(parsed.getScheme())) {
                return context.getContentResolver().openInputStream(parsed);
            }
            return new FileInputStream(source);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void publish() {
        state.setValue(new ImageViewerUiState(
                title,
                !title.isEmpty(),
                story,
                loading,
                remote,
                remoteUrl,
                bitmap,
                entry,
                finishNonce));
    }
}
