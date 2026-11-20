package com.theuncoaffair.fishpipe.analytics;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class WatchlistDb extends SQLiteOpenHelper {
    private static final String NAME = "fishpipe_insights.db";
    private static final int VERSION = 1;
    private static final String TABLE = "watchlist";

    public WatchlistDb(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "unique_id TEXT PRIMARY KEY,"
                + "nickname TEXT,"
                + "avatar TEXT,"
                + "followers INTEGER DEFAULT 0,"
                + "hearts INTEGER DEFAULT 0,"
                + "videos INTEGER DEFAULT 0,"
                + "prev_followers INTEGER DEFAULT 0,"
                + "prev_hearts INTEGER DEFAULT 0,"
                + "prev_videos INTEGER DEFAULT 0,"
                + "updated_at INTEGER DEFAULT 0"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized boolean save(InsightsResult.Profile profile) {
        if (profile == null || profile.uniqueId == null || profile.uniqueId.trim().isEmpty()) {
            return false;
        }
        long prevFollowers = 0;
        long prevHearts = 0;
        long prevVideos = 0;
        Cursor cursor = getReadableDatabase().query(TABLE,
                new String[]{"followers", "hearts", "videos"},
                "unique_id=?", new String[]{profile.uniqueId}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                prevFollowers = cursor.getLong(0);
                prevHearts = cursor.getLong(1);
                prevVideos = cursor.getLong(2);
            }
        } finally {
            cursor.close();
        }
        ContentValues values = new ContentValues();
        values.put("unique_id", profile.uniqueId);
        values.put("nickname", profile.nickname);
        values.put("avatar", profile.avatar);
        values.put("followers", profile.followers);
        values.put("hearts", profile.hearts);
        values.put("videos", profile.videos);
        values.put("prev_followers", prevFollowers);
        values.put("prev_hearts", prevHearts);
        values.put("prev_videos", prevVideos);
        values.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict(TABLE, null, values,
                SQLiteDatabase.CONFLICT_REPLACE) != -1;
    }

    public synchronized List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(TABLE, null, null, null,
                null, null, "updated_at DESC");
        try {
            while (cursor.moveToNext()) out.add(read(cursor));
        } finally {
            cursor.close();
        }
        return out;
    }

    public synchronized void delete(String uniqueId) {
        if (uniqueId == null) return;
        getWritableDatabase().delete(TABLE, "unique_id=?", new String[]{uniqueId});
    }

    private static Entry read(Cursor cursor) {
        Entry entry = new Entry();
        entry.uniqueId = str(cursor, "unique_id");
        entry.nickname = str(cursor, "nickname");
        entry.avatar = str(cursor, "avatar");
        entry.followers = cursor.getLong(cursor.getColumnIndexOrThrow("followers"));
        entry.hearts = cursor.getLong(cursor.getColumnIndexOrThrow("hearts"));
        entry.videos = cursor.getLong(cursor.getColumnIndexOrThrow("videos"));
        entry.prevFollowers = cursor.getLong(cursor.getColumnIndexOrThrow("prev_followers"));
        entry.prevHearts = cursor.getLong(cursor.getColumnIndexOrThrow("prev_hearts"));
        entry.prevVideos = cursor.getLong(cursor.getColumnIndexOrThrow("prev_videos"));
        entry.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return entry;
    }

    private static String str(Cursor cursor, String column) {
        String value = cursor.getString(cursor.getColumnIndexOrThrow(column));
        return value == null ? "" : value;
    }

    public static final class Entry {
        public String uniqueId = "";
        public String nickname = "";
        public String avatar = "";
        public long followers;
        public long hearts;
        public long videos;
        public long prevFollowers;
        public long prevHearts;
        public long prevVideos;
        public long updatedAt;
    }
}
