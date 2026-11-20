package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import com.theuncoaffair.fishpipe.data.DownloadEntry;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MediaProbe {
    public String fileName = "";
    public String filePath = "";
    public String downloadLocation = "";
    public String extension = "";
    public String format = "";
    public long length;
    public long durationMs;
    public int width;
    public int height;
    public float frameRate;
    public String codec = "";
    public long bitRate;
    public int channels;
    public int sampleRate;
    public FileMetadata metadata;

    private MediaProbe() {
    }

    public static MediaProbe read(Context context, DownloadEntry entry) {
        MediaProbe out = new MediaProbe();
        if (context == null || entry == null) return out;
        out.length = Math.max(0, entry.size);
        Uri uri = toUri(entry.localUri);
        if (uri == null) return out;
        readFileFacts(context, entry, uri, out);
        String mime = "";
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }
        if (mime == null) mime = "";
        readMediaFacts(context, uri, out, mime);
        if (DownloadEntry.TYPE_IMAGE.equals(entry.type) && out.width <= 0) {
            readImageBounds(context, uri, out);
        }
        if (out.format.isEmpty()) {
            if (!mime.isEmpty()) out.format = mime;
            else if (!out.extension.isEmpty()) out.format = out.extension.toUpperCase(Locale.US);
        }
        return out;
    }

    private static Uri toUri(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(value);
            if (uri.getScheme() == null) return Uri.fromFile(new File(value));
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    private static void readFileFacts(Context context, DownloadEntry entry, Uri uri, MediaProbe out) {
        String data = "";
        String relative = "";
        String name = "";
        String description = "";
        String tags = "";
        String author = "";
        String artist = "";
        long size = 0;
        if ("content".equals(uri.getScheme())) {
            boolean downloads = uri.toString().contains("/downloads/");
            List<String> columns = new ArrayList<>();
            columns.add(OpenableColumns.DISPLAY_NAME);
            columns.add(OpenableColumns.SIZE);
            columns.add(MediaStore.MediaColumns.DATA);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                columns.add(MediaStore.MediaColumns.RELATIVE_PATH);
                columns.add(MediaStore.MediaColumns.AUTHOR);
                columns.add(MediaStore.MediaColumns.ARTIST);
                if (!downloads) {
                    if (DownloadEntry.TYPE_IMAGE.equals(entry.type)) {
                        columns.add(MediaStore.Images.ImageColumns.DESCRIPTION);
                    } else if (!DownloadEntry.TYPE_MUSIC.equals(entry.type)) {
                        columns.add(MediaStore.Video.VideoColumns.DESCRIPTION);
                        columns.add(MediaStore.Video.VideoColumns.TAGS);
                    }
                }
            }
            String[] full = columns.toArray(new String[0]);
            Cursor c = null;
            try {
                c = context.getContentResolver().query(uri, full, null, null, null);
            } catch (Exception ignored) {
            }
            if (c == null) {
                try {
                    c = context.getContentResolver().query(uri,
                            new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                            null, null, null);
                } catch (Exception ignored) {
                }
            }
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        int si = c.getColumnIndex(OpenableColumns.SIZE);
                        int di = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                        int ri = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                        int ai = c.getColumnIndex(MediaStore.MediaColumns.AUTHOR);
                        int ari = c.getColumnIndex(MediaStore.MediaColumns.ARTIST);
                        int dsi = c.getColumnIndex(MediaStore.Images.ImageColumns.DESCRIPTION);
                        if (dsi < 0) dsi = c.getColumnIndex(MediaStore.Video.VideoColumns.DESCRIPTION);
                        int tsi = c.getColumnIndex(MediaStore.Video.VideoColumns.TAGS);
                        if (ni >= 0) name = safe(c.getString(ni));
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
                        if (di >= 0) data = safe(c.getString(di));
                        if (ri >= 0) relative = safe(c.getString(ri));
                        if (ai >= 0) author = safe(c.getString(ai));
                        if (ari >= 0) artist = safe(c.getString(ari));
                        if (dsi >= 0) description = safe(c.getString(dsi));
                        if (tsi >= 0) tags = safe(c.getString(tsi));
                    }
                } finally {
                    c.close();
                }
            }
        } else if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) data = path;
        }
        if (data.isEmpty() && "file".equals(uri.getScheme())) {
            data = safe(uri.getPath());
        }
        if (data.isEmpty() && !relative.isEmpty() && !name.isEmpty()) {
            String base = Environment.getExternalStorageDirectory().getAbsolutePath();
            String folder = relative.endsWith("/") ? relative : relative + "/";
            data = base + "/" + folder + name;
        }
        if (name.isEmpty() && !data.isEmpty()) {
            name = new File(data).getName();
        }
        out.fileName = name;
        out.filePath = data.isEmpty() ? entry.localUri : data;
        out.downloadLocation = parent(out.filePath);
        out.extension = extension(name);
        if (out.length <= 0) out.length = size;
        out.metadata = FileMetadata.decode(description, tags, author, artist);
    }

    private static void readMediaFacts(Context context, Uri uri, MediaProbe out, String mime) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String trackMime = format.getString(MediaFormat.KEY_MIME);
                if (trackMime == null) continue;
                if (trackMime.startsWith("video/")) {
                    int w = getInt(format, MediaFormat.KEY_WIDTH);
                    int h = getInt(format, MediaFormat.KEY_HEIGHT);
                    int rotation = getInt(format, "rotation-degrees");
                    if (rotation == 90 || rotation == 270) {
                        int swap = w;
                        w = h;
                        h = swap;
                    }
                    if (w > out.width) out.width = w;
                    if (h > out.height) out.height = h;
                    if (out.frameRate <= 0 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        try {
                            out.frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                        } catch (Exception ignored) {
                        }
                    }
                    if (out.bitRate <= 0) out.bitRate = getLong(format, MediaFormat.KEY_BIT_RATE);
                    if (out.durationMs <= 0) out.durationMs = getLong(format, MediaFormat.KEY_DURATION) / 1000L;
                    if (out.codec.isEmpty()) out.codec = codecLabel(trackMime);
                } else if (trackMime.startsWith("audio/")) {
                    if (out.channels <= 0) out.channels = getInt(format, MediaFormat.KEY_CHANNEL_COUNT);
                    if (out.sampleRate <= 0) out.sampleRate = getInt(format, MediaFormat.KEY_SAMPLE_RATE);
                    if (out.bitRate <= 0) out.bitRate = getLong(format, MediaFormat.KEY_BIT_RATE);
                    if (out.durationMs <= 0) out.durationMs = getLong(format, MediaFormat.KEY_DURATION) / 1000L;
                    if (out.codec.isEmpty()) out.codec = codecLabel(trackMime);
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
        if (out.durationMs > 0 && out.width > 0 && out.height > 0 && out.bitRate > 0) return;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            if (out.durationMs <= 0) out.durationMs = toLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (out.width <= 0) out.width = (int) toLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            if (out.height <= 0) out.height = (int) toLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            if (out.bitRate <= 0) out.bitRate = toLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_BITRATE));
            if (out.frameRate <= 0) {
                String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                if (value != null) {
                    try {
                        out.frameRate = Float.parseFloat(value);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        if (out.format.isEmpty() && mime != null && !mime.isEmpty()) out.format = mime;
    }

    private static void readImageBounds(Context context, Uri uri, MediaProbe out) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in != null) BitmapFactory.decodeStream(in, null, options);
            out.width = options.outWidth;
            out.height = options.outHeight;
            if (out.format.isEmpty()) out.format = options.outMimeType == null ? "" : options.outMimeType;
        } catch (Exception ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static int getInt(MediaFormat format, String key) {
        try {
            return format.containsKey(key) ? format.getInteger(key) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long getLong(MediaFormat format, String key) {
        try {
            return format.containsKey(key) ? format.getLong(key) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long toLong(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static String parent(String path) {
        if (path == null || path.isEmpty()) return "";
        int cut = path.lastIndexOf('/');
        return cut > 0 ? path.substring(0, cut) : "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String codecLabel(String mime) {
        String value = mime.toLowerCase(Locale.US);
        if (value.equals("video/avc")) return "H.264 / AVC";
        if (value.equals("video/hevc")) return "H.265 / HEVC";
        if (value.equals("video/x-vnd.on2.vp9")) return "VP9";
        if (value.equals("video/av01")) return "AV1";
        if (value.equals("video/mp4v-es")) return "MPEG-4";
        if (value.equals("audio/mp4a-latm")) return "AAC";
        if (value.equals("audio/mpeg")) return "MP3";
        if (value.equals("audio/opus")) return "Opus";
        if (value.equals("audio/vorbis")) return "Vorbis";
        if (value.equals("audio/flac")) return "FLAC";
        if (value.startsWith("audio/pcm")) return "PCM";
        return mime;
    }
}
