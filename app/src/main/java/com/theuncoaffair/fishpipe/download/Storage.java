package com.theuncoaffair.fishpipe.download;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import com.theuncoaffair.fishpipe.AudioPlayerActivity;
import com.theuncoaffair.fishpipe.PlayerActivity;
import com.theuncoaffair.fishpipe.R;
import com.theuncoaffair.fishpipe.ImageViewerActivity;
import com.theuncoaffair.fishpipe.data.DownloadDb;
import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.data.Settings;
import com.theuncoaffair.fishpipe.di.AppGraph;
import com.theuncoaffair.fishpipe.util.FileMetadata;
import com.theuncoaffair.fishpipe.util.LocalFileProvider;
import com.theuncoaffair.fishpipe.util.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Storage {
    private Storage() {
    }

    public static Saved save(Context context, DownloadEntry entry, File temp, String contentType) throws IOException {
        Settings settings = AppGraph.get(context).settings();
        String mime = mimeFor(entry, contentType);
        String extension = extensionFor(mime, entry);
        entry.downloadedAt = System.currentTimeMillis();
        String name = fileName(entry, extension);
        String custom = normalizePath(settings.getDownloadPath());
        String folder = custom.isEmpty() ? environmentFolder(entry) + "/FishPipe" : custom;
        if (settings.isCreatorFolders() && entry.author != null && !entry.author.trim().isEmpty()) {
            folder = folder + "/" + safe("@" + entry.author.trim(), 48);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveMediaStore(context, entry, temp, name, mime, folder, !custom.isEmpty(),
                    FileMetadata.saved(false, false));
        }
        return saveLegacy(context, entry, temp, name, mime, folder);
    }

    public static Saved saveCaptionVideo(Context context, DownloadEntry entry, File temp)
            throws IOException {
        String videoName = displayName(context, entry.localUri);
        String extension = extension(videoName);
        if (extension.isEmpty()) extension = ".mp4";
        String base = baseName(videoName);
        if (base.isEmpty()) base = baseName(fileName(entry, "mp4"));
        String name = base + "_captions" + extension;
        String folder = videoFolder(context, entry.localUri, entry);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveMediaStore(context, entry, temp, name, "video/mp4", folder, false,
                    FileMetadata.saved(true, false));
        }
        return saveLegacy(context, entry, temp, name, "video/mp4", folder);
    }

    private static String videoFolder(Context context, String localUri, DownloadEntry entry) {
        Uri uri = Uri.parse(localUri);
        if ("content".equals(uri.getScheme())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try (Cursor cursor = context.getContentResolver().query(uri,
                        new String[]{MediaStore.MediaColumns.RELATIVE_PATH}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                        if (index >= 0) {
                            String path = cursor.getString(index);
                            if (path != null && !path.trim().isEmpty()) {
                                path = path.trim();
                                while (path.endsWith("/")) {
                                    path = path.substring(0, path.length() - 1);
                                }
                                return path;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return environmentFolder(entry) + "/FishPipe";
        }
        String path = uri.getPath() == null ? localUri : uri.getPath();
        File parent = new File(path).getParentFile();
        if (parent != null) {
            String absolute = parent.getAbsolutePath();
            String root = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (absolute.equals(root)) return "";
            if (absolute.startsWith(root + "/")) return absolute.substring(root.length() + 1);
        }
        return environmentFolder(entry) + "/FishPipe";
    }

    public static String saveCaption(Context context, DownloadEntry entry, File temp,
                                     Saved video) throws IOException {
        String videoName = displayName(context, video.uri);
        String name = videoName.isEmpty() ? fileName(entry, "srt") : baseName(videoName) + ".srt";
        String folder = video.folder == null ? "" : video.folder;
        if (!folder.isEmpty() && !folder.endsWith("/")) folder += "/";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveCaptionMediaStore(context, temp, name, folder);
        }
        Uri videoUri = Uri.parse(video.uri);
        String path = videoUri.getPath();
        File videoFile = new File(path == null ? video.uri : path);
        File dir = videoFile.getParentFile();
        if (dir == null) throw new IOException("No caption folder");
        File target = new File(dir, name);
        int suffix = 1;
        while (target.exists()) {
            target = new File(dir, baseName(name) + "_" + suffix + extension(name));
            suffix++;
        }
        try (OutputStream out = new FileOutputStream(target)) {
            copy(temp, out);
        }
        scan(context, target);
        return Uri.fromFile(target).toString();
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private static String saveCaptionMediaStore(Context context, File temp, String name,
                                                String folder) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri[] collections = {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        };
        String[] mimes = {"application/x-subrip", "text/plain", "application/octet-stream"};
        Exception last = null;
        for (String mime : mimes) {
            for (int pending = 1; pending >= 0; pending--) {
                for (Uri collection : collections) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
                    if (pending != 0) values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    Uri uri = null;
                    try {
                        uri = resolver.insert(collection, values);
                        if (uri == null) continue;
                        try (OutputStream out = resolver.openOutputStream(uri)) {
                            if (out == null) throw new IOException("No output stream");
                            copy(temp, out);
                        }
                        if (pending != 0) {
                            ContentValues done = new ContentValues();
                            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                            resolver.update(uri, done, null, null);
                        }
                        return uri.toString();
                    } catch (Exception e) {
                        last = e;
                        if (uri != null) {
                            try {
                                resolver.delete(uri, null, null);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        }
        throw new IOException("Caption save failed", last);
    }

    public static void delete(Context context, String localUri) {
        if (localUri == null || localUri.isEmpty()) return;
        deleteCaptionSidecar(context, localUri);
        try {
            Uri uri = Uri.parse(localUri);
            if ("content".equals(uri.getScheme())) {
                context.getContentResolver().delete(uri, null, null);
            } else {
                File file = new File(uri.getPath() == null ? localUri : uri.getPath());
                if (file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                scan(context, file);
            }
        } catch (Exception ignored) {
        }
    }

    private static void deleteCaptionSidecar(Context context, String localUri) {
        try {
            String name = displayName(context, localUri);
            if (name.isEmpty() || name.toLowerCase(Locale.US).endsWith(".srt")) return;
            String sidecar = baseName(name) + ".srt";
            Uri uri = Uri.parse(localUri);
            if ("content".equals(uri.getScheme())) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
                String folder = null;
                try (Cursor cursor = context.getContentResolver().query(uri,
                        new String[]{MediaStore.MediaColumns.RELATIVE_PATH}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                        if (index >= 0) folder = cursor.getString(index);
                    }
                } catch (Exception ignored) {
                }
                if (folder == null) return;
                Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
                String selection = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                        + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
                context.getContentResolver().delete(files, selection,
                        new String[]{folder, sidecar});
                return;
            }
            String path = uri.getPath() == null ? localUri : uri.getPath();
            File parent = new File(path).getParentFile();
            if (parent == null) return;
            File file = new File(parent, sidecar);
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                scan(context, file);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean exists(Context context, DownloadEntry entry) {
        if (entry == null || entry.localUri == null || entry.localUri.trim().isEmpty()) return false;
        Uri uri = Uri.parse(entry.localUri);
        if ("content".equals(uri.getScheme())) {
            try {
                android.content.res.AssetFileDescriptor fd = context.getContentResolver()
                        .openAssetFileDescriptor(uri, "r");
                if (fd == null) return false;
                fd.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        String path = uri.getPath() == null ? entry.localUri : uri.getPath();
        return new File(path).exists();
    }

    public static String displayName(Context context, String localUri) {
        if (localUri == null || localUri.trim().isEmpty()) return "";
        Uri uri = Uri.parse(localUri);
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String name = cursor.getString(index);
                        if (name != null && !name.trim().isEmpty()) return name.trim();
                    }
                }
            } catch (Exception ignored) {
            }
            String last = uri.getLastPathSegment();
            if (last != null) {
                int slash = last.lastIndexOf('/');
                if (slash >= 0) last = last.substring(slash + 1);
                int colon = last.lastIndexOf(':');
                if (colon >= 0) last = last.substring(colon + 1);
                last = Uri.decode(last).trim();
                if (!last.isEmpty() && !last.matches("\\d+")) return last;
            }
            return "";
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) path = localUri;
        try {
            String name = new File(path).getName();
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }

    public static String rename(Context context, DownloadEntry entry, String requested) {
        if (context == null || entry == null || entry.localUri == null
                || entry.localUri.trim().isEmpty()) {
            return null;
        }
        String requestedName = requested == null ? "" : requested.trim();
        if (requestedName.isEmpty()) return null;
        Uri uri = Uri.parse(entry.localUri);
        String current = displayName(context, entry.localUri);
        if (current.isEmpty()) {
            String path = uri.getPath();
            if (path != null) current = new File(path).getName();
        }
        String extension = "";
        int dot = current.lastIndexOf('.');
        if (dot > 0 && dot < current.length() - 1) extension = current.substring(dot);
        if (!requestedName.contains(".") && !extension.isEmpty()) requestedName += extension;
        String clean = Ui.safeName(requestedName);
        if (clean.equals(current)) return entry.localUri;
        try {
            if ("content".equals(uri.getScheme())) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, clean);
                int updated = context.getContentResolver().update(uri, values, null, null);
                return updated > 0 ? entry.localUri : null;
            }
            String path = uri.getPath();
            if (path == null || path.isEmpty()) path = entry.localUri;
            File source = new File(path);
            File parent = source.getParentFile();
            if (parent == null) return null;
            File target = new File(parent, clean);
            if (source.equals(target)) return entry.localUri;
            if (target.exists()) return null;
            if (!source.renameTo(target)) return null;
            scan(context, target);
            String updated = Uri.fromFile(target).toString();
            new DownloadDb(context).updateLocalUri(entry.rowId, updated);
            entry.localUri = updated;
            return updated;
        } catch (Exception e) {
            return null;
        }
    }

    public static void open(Context context, DownloadEntry entry) {
        if (entry.isRunning()) {
            Ui.toast(context, R.string.file_downloading_wait);
            return;
        }
        if (!entry.isComplete() || !exists(context, entry)) {
            Ui.toast(context, R.string.file_not_found);
            return;
        }
        Uri uri = shareUri(context, entry.localUri);
        if (DownloadEntry.TYPE_VIDEO.equals(entry.type) || DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
            Intent player = DownloadEntry.TYPE_VIDEO.equals(entry.type)
                    ? new Intent(context, PlayerActivity.class)
                    : new Intent(context, AudioPlayerActivity.class);
            player.putExtra(PlayerActivity.EXTRA_URL, uri.toString());
            player.putExtra(PlayerActivity.EXTRA_TITLE, entry.title);
            player.putExtra(PlayerActivity.EXTRA_FILE_NAME, displayName(context, entry.localUri));
            player.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                context.startActivity(player);
                return;
            } catch (Exception ignored) {
            }
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeForUri(context, entry, uri));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setDataAndType(uri, "*/*");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(fallback, context.getString(R.string.open_with)));
            } catch (Exception ignored) {
                Ui.toast(context, R.string.file_not_found);
            }
        }
    }

    public static void openWith(Context context, DownloadEntry entry) {
        if (entry.isRunning()) {
            Ui.toast(context, R.string.file_downloading_wait);
            return;
        }
        if (!entry.isComplete() || !exists(context, entry)) {
            Ui.toast(context, R.string.file_not_found);
            return;
        }
        Uri uri = shareUri(context, entry.localUri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeForUri(context, entry, uri));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with)));
        } catch (Exception e) {
            Ui.toast(context, R.string.file_not_found);
        }
    }

    public static void openFolder(Context context, DownloadEntry entry) {
        try {
            String docId = folderDocId(context, entry);
            if (docId == null || docId.trim().isEmpty()) docId = "primary:Download/FishPipe";
            Uri uri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", docId);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "vnd.android.document/directory");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            Ui.toast(context, R.string.open_folder_failed);
        }
    }

    private static String folderDocId(Context context, DownloadEntry entry) {
        if (entry == null || entry.localUri == null || entry.localUri.trim().isEmpty()) return null;
        Uri source = Uri.parse(entry.localUri);
        if ("content".equals(source.getScheme())) {
            String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? new String[]{MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATA}
                    : new String[]{MediaStore.MediaColumns.DATA};
            try (Cursor cursor = context.getContentResolver().query(
                    source, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        int relative = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                        if (relative >= 0) {
                            String docId = docIdFromRelative(cursor.getString(relative));
                            if (docId != null) return docId;
                        }
                    }
                    int data = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                    if (data >= 0) {
                        String docId = docIdFromAbsolute(cursor.getString(data));
                        if (docId != null) return docId;
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        String path = source.getPath();
        if (path == null || path.trim().isEmpty()) return null;
        File parent = new File(path).getParentFile();
        return parent == null ? null : docIdFromAbsolute(parent.getAbsolutePath());
    }

    private static String docIdFromRelative(String path) {
        if (path == null) return null;
        String value = path.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) return null;
        if (value.startsWith("primary:") || value.indexOf(':') > 0) return value;
        return "primary:" + value;
    }

    private static String docIdFromAbsolute(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        String value = path.trim();
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (value.equals(root)) return "primary:";
        if (value.startsWith(root + "/")) return "primary:" + value.substring(root.length() + 1);
        return null;
    }

    public static void share(Context context, DownloadEntry entry) {
        if (!entry.isComplete() || !exists(context, entry)) {
            Ui.toast(context, R.string.file_not_found);
            return;
        }
        Uri uri = shareUri(context, entry.localUri);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeForUri(context, entry, uri));
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, entry.title);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_via)));
        } catch (Exception e) {
            Ui.toast(context, R.string.error_generic);
        }
    }

    public static void updateFileMetadata(Context context, String localUri, DownloadEntry entry,
                                          FileMetadata metadata) {
        if (context == null || entry == null || metadata == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        Uri uri = Uri.parse(localUri == null ? "" : localUri);
        if (!"content".equals(uri.getScheme())) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.AUTHOR, metadata.author);
        values.put(MediaStore.MediaColumns.ARTIST, metadata.author);
        if (!uri.toString().contains("/downloads/")) {
            if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
                values.put(MediaStore.Images.ImageColumns.DESCRIPTION, metadata.encode());
            } else if (!DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
                values.put(MediaStore.Video.VideoColumns.DESCRIPTION, metadata.encode());
                values.put(MediaStore.Video.VideoColumns.TAGS, metadata.encode());
            }
        }
        try {
            context.getContentResolver().update(uri, values, null, null);
        } catch (Exception ignored) {
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private static Saved saveMediaStore(Context context, DownloadEntry entry, File temp,
                                        String name, String mime, String folder,
                                        boolean custom, FileMetadata metadata) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection;
        if (custom) {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        } else if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        } else {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        if (!custom && DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
            values.put(MediaStore.Audio.Media.IS_MUSIC, 1);
        }
        if (metadata != null) metadata.write(values, entry, custom);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) throw new IOException("MediaStore insert failed");
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("No output stream");
            copy(temp, out);
        } catch (Exception e) {
            try {
                resolver.delete(uri, null, null);
            } catch (Exception ignored) {
            }
            throw new IOException(e);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return new Saved(uri.toString(), temp.length(), folder);
    }

    private static Saved saveLegacy(Context context, DownloadEntry entry, File temp,
                                    String name, String mime, String folder) throws IOException {
        File dir = new File(Environment.getExternalStorageDirectory(), folder);
        boolean shared = canWriteShared(context, dir);
        if (!shared) {
            dir = new File(context.getExternalFilesDir(null), folder);
        }
        if (dir == null) dir = new File(context.getFilesDir(), "FishPipe");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create folder");
        File target = new File(dir, name);
        int suffix = 1;
        while (target.exists()) {
            target = new File(dir, baseName(name) + "_" + suffix + extension(name));
            suffix++;
        }
        try (OutputStream out = new FileOutputStream(target)) {
            copy(temp, out);
        }
        scan(context, target);
        return new Saved(Uri.fromFile(target).toString(), target.length(),
                shared ? folder : target.getParent());
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        String value = path.trim().replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty() || value.contains("..")) return "";
        StringBuilder clean = new StringBuilder();
        for (String part : value.split("/+")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if (clean.length() > 0) clean.append('/');
            clean.append(part);
        }
        return clean.toString();
    }

    private static boolean canWriteShared(Context context, File dir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (!dir.exists() && !dir.mkdirs()) return false;
        return dir.canWrite();
    }

    private static String environmentFolder(DownloadEntry entry) {
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) return Environment.DIRECTORY_PICTURES;
        if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) return Environment.DIRECTORY_MUSIC;
        return Environment.DIRECTORY_MOVIES;
    }

    private static void copy(File temp, OutputStream out) throws IOException {
        try (InputStream in = new FileInputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
        }
    }

    private static String fileName(DownloadEntry entry, String extension) {
        String creator = safe(entry.author == null || entry.author.trim().isEmpty()
                ? "unknown" : entry.author.trim(), 40);
        String uploaded = stamp(entry.uploadedAt);
        String downloaded = stamp(entry.downloadedAt);
        return creator + "_" + uploaded + "_" + downloaded + "." + extension;
    }

    private static String safe(String value, int max) {
        String clean = Ui.safeName(value);
        if (clean.length() > max) clean = clean.substring(0, max).trim();
        return clean.isEmpty() ? "media" : clean;
    }

    private static String stamp(long time) {
        if (time <= 0) return "unknown";
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(time));
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    private static String extensionFor(String mime, DownloadEntry entry) {
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
            if (mime.contains("webp")) return "webp";
            if (mime.contains("png")) return "png";
            if (mime.contains("gif")) return "gif";
            return "jpg";
        }
        if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
            if (mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac")) return "m4a";
            return "mp3";
        }
        return "mp4";
    }

    private static String mimeFor(DownloadEntry entry, String contentType) {
        if (contentType != null && contentType.contains("/")) {
            String value = contentType.split(";")[0].trim().toLowerCase(Locale.US);
            if (DownloadEntry.TYPE_VIDEO.equals(entry.type) && value.startsWith("video/")) return value;
            if (DownloadEntry.TYPE_IMAGE.equals(entry.type) && value.startsWith("image/")) return value;
            if (DownloadEntry.TYPE_MUSIC.equals(entry.type) && value.startsWith("audio/")) return value;
        }
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(entry.remoteUrl == null ? "" : entry.remoteUrl);
            if (ext != null && ext.length() > 0) {
                String value = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (value != null && value.startsWith("image/")) return value;
            }
            return "image/jpeg";
        }
        if (DownloadEntry.TYPE_MUSIC.equals(entry.type)) return "audio/mpeg";
        return "video/mp4";
    }

    private static String mimeForUri(Context context, DownloadEntry entry, Uri uri) {
        try {
            String type = context.getContentResolver().getType(uri);
            if (type != null && type.contains("/")) return type;
        } catch (Exception ignored) {
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (extension != null && !extension.isEmpty()) {
            String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.US));
            if (type != null && type.contains("/")) return type;
        }
        return Ui.mime(entry);
    }

    private static Uri shareUri(Context context, String localUri) {
        Uri uri = Uri.parse(localUri);
        if ("content".equals(uri.getScheme())) return uri;
        File file = new File(uri.getPath() == null ? localUri : uri.getPath());
        return LocalFileProvider.uriFor(file);
    }

    private static void scan(Context context, File file) {
        try {
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null, null);
        } catch (Exception ignored) {
        }
    }

    public static final class Saved {
        public final String uri;
        public final long size;
        public final String folder;

        Saved(String uri, long size, String folder) {
            this.uri = uri;
            this.size = size;
            this.folder = folder;
        }
    }
}
