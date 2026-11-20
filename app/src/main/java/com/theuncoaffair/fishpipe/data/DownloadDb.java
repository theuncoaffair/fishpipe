package com.theuncoaffair.fishpipe.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class DownloadDb extends SQLiteOpenHelper {
    private static final String NAME = "fishpipe.db";
    private static final int VERSION = 9;
    private static final String TABLE = "downloads";
    private static final String CREATORS = "creators";

    public DownloadDb(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "media_id TEXT,"
                + "title TEXT,"
                + "author TEXT,"
                + "author_avatar TEXT,"
                + "caption TEXT,"
                + "hashtags TEXT,"
                + "page_url TEXT,"
                + "type TEXT,"
                + "remote_url TEXT,"
                + "local_uri TEXT,"
                + "thumb_url TEXT,"
                + "quality TEXT,"
                + "story INTEGER DEFAULT 0,"
                + "slideshow INTEGER DEFAULT 0,"
                + "slideshow_images TEXT,"
                + "slideshow_audio TEXT,"
                + "caption_mode TEXT,"
                + "caption_url TEXT,"
                + "caption_language TEXT,"
                + "caption_auto INTEGER DEFAULT 0,"
                + "size INTEGER DEFAULT 0,"
                + "bytes INTEGER DEFAULT 0,"
                + "status TEXT,"
                + "hidden INTEGER DEFAULT 0,"
                + "created_at INTEGER DEFAULT 0,"
                + "uploaded_at INTEGER DEFAULT 0,"
                + "downloaded_at INTEGER DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX idx_downloads_created ON " + TABLE + " (created_at DESC)");
        createCreators(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN uploaded_at INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN downloaded_at INTEGER DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN author_avatar TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN caption TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN hashtags TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN page_url TEXT");
        }
        if (oldVersion < 4) {
            createCreators(db);
            db.execSQL("INSERT OR IGNORE INTO " + CREATORS
                    + " (author, avatar, created_at, updated_at) "
                    + "SELECT author, MAX(author_avatar), MAX(created_at), MAX(created_at) FROM "
                    + TABLE + " WHERE hidden=0 AND author IS NOT NULL AND author!='' GROUP BY author");
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN story INTEGER DEFAULT 0");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN slideshow INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN slideshow_images TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN slideshow_audio TEXT");
        }
        if (oldVersion < 7) {
            sanitizeCaptions(db);
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN caption_mode TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN caption_url TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN caption_language TEXT");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN caption_auto INTEGER DEFAULT 0");
        }
        if (oldVersion < 9) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN quality TEXT");
        }
    }

    private static void sanitizeCaptions(SQLiteDatabase db) {
        List<long[]> ids = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        Cursor c = db.query(TABLE, new String[]{"_id", "title", "caption"},
                null, null, null, null, null);
        try {
            while (c.moveToNext()) {
                String title = c.getString(1);
                String caption = c.getString(2);
                String source = title == null || title.trim().isEmpty() ? caption : title;
                ids.add(new long[]{c.getLong(0)});
                sources.add(source == null ? "" : source);
            }
        } finally {
            c.close();
        }
        for (int i = 0; i < ids.size(); i++) {
            ContentValues values = new ContentValues();
            values.put("caption", MediaItem.captionOf(sources.get(i)));
            values.put("hashtags", MediaItem.hashtagsOf(sources.get(i)));
            db.update(TABLE, values, "_id=?", new String[]{String.valueOf(ids.get(i)[0])});
        }
    }

    private static void createCreators(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + CREATORS + " ("
                + "author TEXT PRIMARY KEY,"
                + "avatar TEXT,"
                + "created_at INTEGER DEFAULT 0,"
                + "updated_at INTEGER DEFAULT 0"
                + ")");
    }

    public synchronized long insert(DownloadEntry e) {
        ContentValues v = new ContentValues();
        v.put("media_id", e.mediaId);
        v.put("title", e.title);
        v.put("author", e.author);
        v.put("author_avatar", e.authorAvatar);
        v.put("caption", e.caption);
        v.put("hashtags", e.hashtags);
        v.put("page_url", e.pageUrl);
        v.put("type", e.type);
        v.put("remote_url", e.remoteUrl);
        v.put("local_uri", e.localUri);
        v.put("thumb_url", e.thumbUrl);
        v.put("quality", e.quality);
        v.put("story", e.story ? 1 : 0);
        v.put("slideshow", e.slideshow ? 1 : 0);
        v.put("slideshow_images", e.slideshowImages);
        v.put("slideshow_audio", e.slideshowAudio);
        v.put("caption_mode", e.captionMode);
        v.put("caption_url", e.captionUrl);
        v.put("caption_language", e.captionLanguage);
        v.put("caption_auto", e.captionAuto ? 1 : 0);
        v.put("size", e.size);
        v.put("bytes", e.bytes);
        v.put("status", e.status);
        v.put("hidden", e.hidden);
        v.put("created_at", e.createdAt > 0 ? e.createdAt : System.currentTimeMillis());
        v.put("uploaded_at", e.uploadedAt);
        v.put("downloaded_at", e.downloadedAt);
        SQLiteDatabase db = getWritableDatabase();
        long id = db.insert(TABLE, null, v);
        e.rowId = id;
        ensureCreator(db, e.author, e.authorAvatar, System.currentTimeMillis());
        return id;
    }

    private static void ensureCreator(SQLiteDatabase db, String author, String avatar, long now) {
        if (author == null || author.trim().isEmpty()) return;
        ContentValues row = new ContentValues();
        row.put("author", author);
        row.put("avatar", avatar == null ? "" : avatar);
        row.put("created_at", now);
        row.put("updated_at", now);
        db.insertWithOnConflict(CREATORS, null, row, SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues touch = new ContentValues();
        touch.put("updated_at", now);
        if (avatar != null && !avatar.trim().isEmpty()) touch.put("avatar", avatar);
        db.update(CREATORS, touch, "author=?", new String[]{author});
    }

    public synchronized void updateProgress(long id, long bytes, long size, String status) {
        ContentValues v = new ContentValues();
        v.put("bytes", bytes);
        if (size > 0) v.put("size", size);
        if (status != null) v.put("status", status);
        getWritableDatabase().update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void complete(long id, String localUri, long size, String status) {
        ContentValues v = new ContentValues();
        v.put("local_uri", localUri);
        v.put("size", size);
        v.put("bytes", size);
        v.put("status", status);
        v.put("downloaded_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearCaption(long id) {
        ContentValues v = new ContentValues();
        v.put("caption_mode", "");
        v.put("caption_url", "");
        v.put("caption_language", "");
        v.put("caption_auto", 0);
        getWritableDatabase().update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateLocalUri(long id, String localUri) {
        ContentValues v = new ContentValues();
        v.put("local_uri", localUri == null ? "" : localUri);
        getWritableDatabase().update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void resetForRedownload(long id) {
        ContentValues v = new ContentValues();
        v.put("local_uri", "");
        v.put("size", 0);
        v.put("bytes", 0);
        v.put("status", DownloadEntry.STATUS_QUEUED);
        v.put("downloaded_at", 0);
        getWritableDatabase().update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void setStatus(long id, String status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        getWritableDatabase().update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void requeueRunning() {
        ContentValues v = new ContentValues();
        v.put("status", DownloadEntry.STATUS_QUEUED);
        v.put("bytes", 0);
        getWritableDatabase().update(TABLE, v, "status=?",
                new String[]{DownloadEntry.STATUS_RUNNING});
    }

    public synchronized void setHidden(long id, boolean hidden) {
        ContentValues v = new ContentValues();
        v.put("hidden", hidden ? 1 : 0);
        getWritableDatabase().update(TABLE, v, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete(TABLE, "_id=?", new String[]{String.valueOf(id)});
    }


    public synchronized List<DownloadEntry> listByType(String type, boolean includeHidden) {
        List<DownloadEntry> out = new ArrayList<>();
        String selection = includeHidden ? "type=?" : "hidden=0 AND type=?";
        Cursor c = getReadableDatabase().query(TABLE, null, selection,
                new String[]{type}, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized List<DownloadEntry> listForPlayback(String type) {
        List<DownloadEntry> out = new ArrayList<>();
        if (type == null || type.trim().isEmpty()) return out;
        String sql = "SELECT d.* FROM " + TABLE + " d "
                + "WHERE d.hidden=0 AND d.status=? AND d.type=? "
                + "AND d.local_uri IS NOT NULL AND d.local_uri!='' "
                + "ORDER BY (SELECT MAX(CASE WHEN x.downloaded_at > 0 THEN x.downloaded_at "
                + "ELSE x.created_at END) FROM " + TABLE + " x "
                + "WHERE x.hidden=0 AND x.status=? AND x.type=d.type AND x.author=d.author) DESC, "
                + "d.author COLLATE NOCASE ASC, "
                + "CASE WHEN d.downloaded_at > 0 THEN d.downloaded_at ELSE d.created_at END DESC, "
                + "d._id DESC";
        Cursor c = getReadableDatabase().rawQuery(sql,
                new String[]{DownloadEntry.STATUS_COMPLETED, type,
                        DownloadEntry.STATUS_COMPLETED});
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized boolean hasMediaId(String mediaId) {
        if (mediaId == null || mediaId.trim().isEmpty()) return false;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"_id"}, "media_id=? AND hidden=0",
                new String[]{mediaId}, null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public synchronized List<DownloadEntry> listByMediaIdAndType(String mediaId, String type) {
        List<DownloadEntry> out = new ArrayList<>();
        if (mediaId == null || mediaId.trim().isEmpty()) return out;
        Cursor c = getReadableDatabase().query(TABLE, null,
                "media_id=? AND type=? AND hidden=0",
                new String[]{mediaId, type}, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized DownloadEntry findByLocalUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) return null;
        Cursor c = getReadableDatabase().query(TABLE, null, "local_uri=?",
                new String[]{uri}, null, null, null, "1");
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    public synchronized List<DownloadEntry> listByRemoteUrl(String url) {
        List<DownloadEntry> out = new ArrayList<>();
        if (url == null || url.trim().isEmpty()) return out;
        Cursor c = getReadableDatabase().query(TABLE, null,
                "remote_url=? AND hidden=0", new String[]{url},
                null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized boolean hasRemoteUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"_id"},
                "remote_url=? AND hidden=0", new String[]{url}, null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public synchronized List<DownloadEntry> listByAuthor(String author) {
        List<DownloadEntry> out = new ArrayList<>();
        if (author == null || author.trim().isEmpty()) return out;
        Cursor c = getReadableDatabase().query(TABLE, null,
                "author=? AND hidden=0", new String[]{author},
                null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized boolean hasSlideshow(String mediaId) {
        if (mediaId == null || mediaId.trim().isEmpty()) return false;
        Cursor c = getReadableDatabase().query(TABLE, new String[]{"_id"},
                "media_id=? AND slideshow=1 AND hidden=0", new String[]{mediaId},
                null, null, null, "1");
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public synchronized List<DownloadEntry> existingFor(MediaItem item) {
        List<DownloadEntry> out = new ArrayList<>();
        if (item == null) return out;
        if (hasSlideshow(item.id)) out.addAll(listByMediaIdAndType(item.id, DownloadEntry.TYPE_VIDEO));
        if (item.hasImages()) {
            return photosFor(item);
        }
        if (item.hasVideo()) {
            return listByMediaIdAndType(item.id, DownloadEntry.TYPE_VIDEO);
        }
        if (item.hasMusic()) return listByRemoteUrl(item.musicUrl);
        if (item.hasCover()) return listByRemoteUrl(item.coverUrl);
        return listByMediaId(item.id);
    }

    public synchronized List<DownloadEntry> photosFor(MediaItem item) {
        List<DownloadEntry> out = new ArrayList<>();
        if (item == null || !item.hasImages()) return out;
        for (DownloadEntry entry : listByMediaIdAndType(item.id, DownloadEntry.TYPE_IMAGE)) {
            if (MediaItem.notEmpty(item.coverUrl) && item.coverUrl.equals(entry.remoteUrl)) continue;
            out.add(entry);
        }
        return out;
    }

    public synchronized boolean isSaved(MediaItem item) {
        return !existingFor(item).isEmpty();
    }

    public synchronized List<DownloadEntry> listByMediaId(String mediaId) {
        List<DownloadEntry> out = new ArrayList<>();
        if (mediaId == null || mediaId.trim().isEmpty()) return out;
        Cursor c = getReadableDatabase().query(TABLE, null, "media_id=? AND hidden=0",
                new String[]{mediaId}, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized List<CreatorInfo> listCreators() {
        List<CreatorInfo> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT c.author AS author, COALESCE(NULLIF(c.avatar,''), MAX(d.author_avatar), '') AS avatar, "
                        + "COALESCE(MAX(d.thumb_url), '') AS thumb, COUNT(d._id) AS total "
                        + "FROM " + CREATORS + " c INNER JOIN " + TABLE + " d "
                        + "ON d.author=c.author AND d.hidden=0 "
                        + "GROUP BY c.author ORDER BY c.updated_at DESC, c.author ASC", null);
        try {
            while (c.moveToNext()) {
                String avatar = c.getString(c.getColumnIndexOrThrow("avatar"));
                if (avatar == null || avatar.trim().isEmpty()) {
                    avatar = c.getString(c.getColumnIndexOrThrow("thumb"));
                }
                out.add(new CreatorInfo(c.getString(c.getColumnIndexOrThrow("author")),
                        avatar == null ? "" : avatar, c.getInt(c.getColumnIndexOrThrow("total"))));
            }
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized void deleteCreator(String author) {
        if (author == null || author.trim().isEmpty()) return;
        getWritableDatabase().delete(CREATORS, "author=?", new String[]{author});
    }

    public synchronized void deleteByAuthor(String author) {
        if (author == null || author.trim().isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, "author=?", new String[]{author});
            db.delete(CREATORS, "author=?", new String[]{author});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void deleteMany(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        StringBuilder where = new StringBuilder("_id IN (");
        String[] args = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) where.append(',');
            where.append('?');
            args[i] = String.valueOf(ids.get(i));
        }
        where.append(')');
        getWritableDatabase().delete(TABLE, where.toString(), args);
    }

    public synchronized void clearHistory() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, null, null);
            db.delete(CREATORS, null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void clearHistoryByType(String type) {
        if (type == null || type.trim().isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE, "type=?", new String[]{type});
            db.execSQL("DELETE FROM " + CREATORS
                    + " WHERE author NOT IN (SELECT author FROM " + TABLE
                    + " WHERE author IS NOT NULL AND author!='')");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void clearCreators() {
        getWritableDatabase().delete(CREATORS, null, null);
    }

    public synchronized DownloadEntry get(long id) {
        Cursor c = getReadableDatabase().query(TABLE, null, "_id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    public synchronized List<DownloadEntry> list(boolean includeHidden) {
        List<DownloadEntry> out = new ArrayList<>();
        String selection = includeHidden ? null : "hidden=0";
        Cursor c = getReadableDatabase().query(TABLE, null, selection, null, null, null, "created_at DESC");
        try {
            while (c.moveToNext()) out.add(read(c));
        } finally {
            c.close();
        }
        return out;
    }

    public synchronized DownloadEntry nextQueued() {
        Cursor c = getReadableDatabase().query(TABLE, null, "status=?",
                new String[]{DownloadEntry.STATUS_QUEUED}, null, null, "created_at ASC", "1");
        try {
            return c.moveToFirst() ? read(c) : null;
        } finally {
            c.close();
        }
    }

    public synchronized int pendingCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE status=?",
                new String[]{DownloadEntry.STATUS_QUEUED});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public synchronized int runningCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE status IN (?,?)",
                new String[]{DownloadEntry.STATUS_QUEUED, DownloadEntry.STATUS_RUNNING});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private static DownloadEntry read(Cursor c) {
        DownloadEntry e = new DownloadEntry();
        e.rowId = c.getLong(c.getColumnIndexOrThrow("_id"));
        e.mediaId = str(c, "media_id");
        e.title = str(c, "title");
        e.author = str(c, "author");
        e.authorAvatar = str(c, "author_avatar");
        e.caption = str(c, "caption");
        e.hashtags = str(c, "hashtags");
        e.pageUrl = str(c, "page_url");
        e.type = str(c, "type");
        e.remoteUrl = str(c, "remote_url");
        e.localUri = str(c, "local_uri");
        e.thumbUrl = str(c, "thumb_url");
        e.quality = str(c, "quality");
        e.story = c.getInt(c.getColumnIndexOrThrow("story")) != 0;
        e.slideshow = c.getInt(c.getColumnIndexOrThrow("slideshow")) != 0;
        e.slideshowImages = str(c, "slideshow_images");
        e.slideshowAudio = str(c, "slideshow_audio");
        e.captionMode = str(c, "caption_mode");
        e.captionUrl = str(c, "caption_url");
        e.captionLanguage = str(c, "caption_language");
        e.captionAuto = c.getInt(c.getColumnIndexOrThrow("caption_auto")) != 0;
        e.size = c.getLong(c.getColumnIndexOrThrow("size"));
        e.bytes = c.getLong(c.getColumnIndexOrThrow("bytes"));
        e.status = str(c, "status");
        e.hidden = c.getInt(c.getColumnIndexOrThrow("hidden"));
        e.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        e.uploadedAt = c.getLong(c.getColumnIndexOrThrow("uploaded_at"));
        e.downloadedAt = c.getLong(c.getColumnIndexOrThrow("downloaded_at"));
        return e;
    }

    private static String str(Cursor c, String column) {
        String value = c.getString(c.getColumnIndexOrThrow(column));
        return value == null ? "" : value;
    }
}
