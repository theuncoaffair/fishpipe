package com.theuncoaffair.fishpipe.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class LocalFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.theuncoaffair.fishpipe.files";

    public static Uri uriFor(File file) {
        return Uri.parse("content://" + AUTHORITY + "/file/" + Uri.encode(file.getAbsolutePath()));
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".mp3") || lower.endsWith(".m4a")) return "audio/*";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".mp4")) return "video/mp4";
            if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
            if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain";
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] columns = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cursor = new MatrixCursor(columns, 1);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
                else row[i] = null;
            }
            cursor.addRow(row);
            return cursor;
        } catch (Exception e) {
            return null;
        }
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        String path = uri.getLastPathSegment();
        if (path == null) throw new FileNotFoundException("Missing path");
        File file = new File(Uri.decode(path));
        try {
            String canonical = file.getCanonicalPath();
            boolean allowed = canonical.startsWith(Environment.getExternalStorageDirectory().getCanonicalPath())
                    || (getContext() != null && canonical.startsWith(getContext().getFilesDir().getCanonicalPath()))
                    || (getContext() != null && canonical.startsWith(getContext().getCacheDir().getCanonicalPath()));
            if (!allowed || !file.isFile()) throw new FileNotFoundException(canonical);
            return file;
        } catch (Exception e) {
            throw new FileNotFoundException(path);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
