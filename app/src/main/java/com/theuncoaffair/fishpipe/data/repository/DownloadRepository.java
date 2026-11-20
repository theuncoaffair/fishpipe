package com.theuncoaffair.fishpipe.data.repository;

import android.content.Context;

import com.theuncoaffair.fishpipe.data.CreatorInfo;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.MediaItem;
import com.theuncoaffair.fishpipe.download.DownloadController;
import com.theuncoaffair.fishpipe.download.Storage;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class DownloadRepository {
    private final Context context;
    private final DownloadDb db;

    @Inject
    public DownloadRepository(@ApplicationContext Context context) {
        this.context = context;
        this.db = new DownloadDb(context);
    }

    public List<DownloadEntry> listByType(String type, boolean includeHidden) {
        return db.listByType(type, includeHidden);
    }

    public List<CreatorInfo> listCreators() {
        return db.listCreators();
    }

    public List<DownloadEntry> listByAuthor(String author) {
        return db.listByAuthor(author);
    }

    public List<DownloadEntry> list(boolean includeHidden) {
        return db.list(includeHidden);
    }

    public int runningCount() {
        return db.runningCount();
    }

    public boolean hasMediaId(String mediaId) {
        return db.hasMediaId(mediaId);
    }

    public void cancel(long rowId) {
        DownloadController.cancel(context, rowId);
    }

    public void pause(long rowId) {
        DownloadController.pause(context, rowId);
    }

    public void resume(long rowId) {
        DownloadController.resume(context, rowId);
    }

    public void retry(long rowId) {
        DownloadController.retry(context, rowId);
    }

    public void start() {
        DownloadController.start(context);
    }

    public int enqueue(MediaItem item, String kind, boolean preferHd, boolean captions,
                       String captionMode) {
        return DownloadController.enqueue(context, item, kind, preferHd, captions, captionMode);
    }

    public int enqueueDefaultVideo(MediaItem item) {
        return DownloadController.enqueueDefaultVideo(context, item);
    }

    public int enqueueDefaultPhotos(MediaItem item) {
        return DownloadController.enqueueDefaultPhotos(context, item);
    }

    public int enqueueDefaultAudio(MediaItem item) {
        return DownloadController.enqueueDefaultAudio(context, item);
    }

    public int enqueueImages(MediaItem item, List<String> images, boolean withMusic) {
        return DownloadController.enqueueImages(context, item, images, withMusic);
    }

    public int enqueueSlideshow(MediaItem item) {
        return DownloadController.enqueueSlideshow(context, item);
    }

    public void deleteEntries(List<DownloadEntry> entries, boolean deleteFiles) {
        List<Long> ids = new ArrayList<>();
        for (DownloadEntry entry : entries) {
            if (entry.isRunning()) cancel(entry.rowId);
            if (deleteFiles) Storage.delete(context, entry.localUri);
            ids.add(entry.rowId);
        }
        db.deleteMany(ids);
    }

    public void deleteByAuthor(String author, boolean deleteFiles) {
        for (DownloadEntry entry : db.listByAuthor(author)) {
            if (entry.isRunning()) cancel(entry.rowId);
            if (deleteFiles) Storage.delete(context, entry.localUri);
        }
        db.deleteByAuthor(author);
    }

    public void clearHistoryByType(String type, boolean deleteFiles) {
        for (DownloadEntry entry : db.listByType(type, true)) {
            if (entry.isRunning()) cancel(entry.rowId);
            if (deleteFiles) Storage.delete(context, entry.localUri);
        }
        db.clearHistoryByType(type);
    }

    public void clearHistory(boolean deleteFiles) {
        for (DownloadEntry entry : db.list(true)) {
            if (entry.isRunning()) cancel(entry.rowId);
            if (deleteFiles) Storage.delete(context, entry.localUri);
        }
        db.clearHistory();
    }

    public void clearCreators() {
        db.clearCreators();
    }
}
