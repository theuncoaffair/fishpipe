package com.theuncoaffair.fishpipe.util;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.provider.MediaStore;

import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.data.DownloadEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FileMetadata {
    public static final String AUTHOR = "fishpipe";
    public static final String KEY_AUTHOR = "author";
    public static final String KEY_EMBEDDED_CAPTION = "embedded_caption";
    public static final String KEY_HAS_SRT = "has_srt";
    private static final String VALUE_YES = "yes";
    private static final String VALUE_NO = "no";

    public final String author;
    public final boolean embeddedCaption;
    public final boolean hasSrt;

    public FileMetadata(String author, boolean embeddedCaption, boolean hasSrt) {
        this.author = author == null ? "" : author;
        this.embeddedCaption = embeddedCaption;
        this.hasSrt = hasSrt;
    }

    public static FileMetadata saved(boolean embeddedCaption, boolean hasSrt) {
        return new FileMetadata(AUTHOR, embeddedCaption, hasSrt);
    }

    public String encode() {
        return KEY_AUTHOR + "=" + author + ";"
                + KEY_EMBEDDED_CAPTION + "=" + (embeddedCaption ? VALUE_YES : VALUE_NO) + ";"
                + KEY_HAS_SRT + "=" + (hasSrt ? VALUE_YES : VALUE_NO);
    }

    public static FileMetadata decode(String description, String tags,
                                      String authorColumn, String artistColumn) {
        Map<String, String> values = parse(description);
        if (values.isEmpty()) values = parse(tags);
        if (!values.containsKey(KEY_AUTHOR) && !values.containsKey(KEY_EMBEDDED_CAPTION)
                && !values.containsKey(KEY_HAS_SRT)) {
            values.clear();
        }
        if (values.isEmpty()) {
            String fallback = authorColumn == null || authorColumn.trim().isEmpty()
                    ? artistColumn : authorColumn;
            if (fallback == null || fallback.trim().isEmpty()) return null;
            return new FileMetadata(fallback.trim(), false, false);
        }
        String author = values.get(KEY_AUTHOR);
        if (author == null || author.trim().isEmpty()) author = AUTHOR;
        return new FileMetadata(author,
                VALUE_YES.equals(values.get(KEY_EMBEDDED_CAPTION)),
                VALUE_YES.equals(values.get(KEY_HAS_SRT)));
    }

    public void write(ContentValues values, DownloadEntry entry, boolean downloadsCollection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        values.put(MediaStore.MediaColumns.AUTHOR, author);
        values.put(MediaStore.MediaColumns.ARTIST, author);
        if (downloadsCollection) return;
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
            values.put(MediaStore.Images.ImageColumns.DESCRIPTION, encode());
        } else if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
            return;
        } else {
            values.put(MediaStore.Video.VideoColumns.DESCRIPTION, encode());
            values.put(MediaStore.Video.VideoColumns.TAGS, encode());
        }
    }

    public List<Field> fields(Context context) {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field(R.string.info_file_author, author));
        fields.add(new Field(R.string.info_embedded_caption,
                context.getString(embeddedCaption ? R.string.yes : R.string.no)));
        fields.add(new Field(R.string.info_has_srt,
                context.getString(hasSrt ? R.string.yes : R.string.no)));
        return fields;
    }

    private static Map<String, String> parse(String value) {
        Map<String, String> values = new HashMap<>();
        if (value == null) return values;
        for (String part : value.split(";")) {
            int cut = part.indexOf('=');
            if (cut <= 0) continue;
            values.put(part.substring(0, cut).trim(), part.substring(cut + 1).trim());
        }
        return values;
    }

    public static final class Field {
        public final int labelRes;
        public final String value;

        Field(int labelRes, String value) {
            this.labelRes = labelRes;
            this.value = value;
        }
    }
}
