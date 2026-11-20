package com.theuncoaffair.fishpipe.util;

import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.theuncoaffair.fishpipe.net.LinkUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long MAX_DISK_BYTES = 64L * 1024 * 1024;
    private static final String DISK_DIR = "image_cache";
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    public static void clearCache() {
        CACHE.evictAll();
    }

    public static void load(ImageView view, String url, int placeholder) {
        if (view == null) return;
        view.setTag(url == null ? "" : url);
        if (url == null || url.trim().isEmpty()) {
            view.setImageResource(placeholder);
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(placeholder);
        final Context app = view.getContext().getApplicationContext();
        if (isLocal(url)) {
            POOL.execute(() -> {
                Bitmap bitmap = readLocal(app, url);
                if (bitmap == null) return;
                CACHE.put(url, bitmap);
                MAIN.post(() -> {
                    Object tag = view.getTag();
                    if (tag != null && tag.equals(url)) view.setImageBitmap(bitmap);
                });
            });
            return;
        }
        POOL.execute(() -> {
            Bitmap bitmap = readDisk(app, url);
            if (bitmap == null) {
                byte[] data = downloadBytes(url);
                if (data == null) return;
                bitmap = decode(data);
                if (bitmap == null) return;
                writeDisk(app, url, data);
            }
            final Bitmap result = bitmap;
            CACHE.put(url, result);
            MAIN.post(() -> {
                Object tag = view.getTag();
                if (tag != null && tag.equals(url)) view.setImageBitmap(result);
            });
        });
    }

    private static boolean isLocal(String url) {
        return url.startsWith("content://") || url.startsWith("file://") || url.startsWith("/");
    }

    private static Bitmap readLocal(Context context, String url) {
        InputStream in = null;
        try {
            if (url.startsWith("/")) in = new FileInputStream(url);
            else in = context.getContentResolver().openInputStream(Uri.parse(url));
            if (in == null) return null;
            byte[] data = readBytes(in, MAX_BYTES);
            return data == null ? null : decode(data);
        } catch (Exception ignored) {
            return null;
        } finally {
            close(in);
        }
    }

    private static byte[] downloadBytes(String url) {
        int[] status = new int[1];
        byte[] data = downloadBytes(url, true, status);
        if (data == null && (status[0] == 401 || status[0] == 403 || status[0] == 429)) {
            data = downloadBytes(url, false, status);
        }
        return data;
    }

    private static byte[] downloadBytes(String url, boolean withHeaders, int[] status) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "image/*,*/*;q=0.8");
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (withHeaders) {
                conn.setRequestProperty("Referer", "https://www.tiktok.com/");
                conn.setRequestProperty("Cookie", "tt_webid_v2=" + LinkUtils.randomWid());
            }
            int code = conn.getResponseCode();
            status[0] = code;
            if (code < 200 || code >= 300) return null;
            in = conn.getInputStream();
            return readBytes(in, MAX_BYTES);
        } catch (Exception ignored) {
            return null;
        } finally {
            close(in);
            if (conn != null) conn.disconnect();
        }
    }

    private static Bitmap readDisk(Context context, String url) {
        File file = diskFile(context, url);
        if (file == null || !file.isFile()) return null;
        long length = file.length();
        if (length <= 0 || length > MAX_BYTES) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] data = readBytes(in, MAX_BYTES);
            if (data == null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                return null;
            }
            Bitmap bitmap = decode(data);
            if (bitmap == null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                return null;
            }
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(System.currentTimeMillis());
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            close(in);
        }
    }

    private static void writeDisk(Context context, String url, byte[] data) {
        File file = diskFile(context, url);
        if (file == null) return;
        File temp = new File(file.getPath() + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(temp);
            out.write(data);
            out.flush();
            close(out);
            out = null;
            if (!temp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                return;
            }
            trimDisk(context);
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        } finally {
            close(out);
        }
    }

    private static Bitmap decode(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > 600) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static byte[] readBytes(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[16 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) != -1) {
            total += n;
            if (total > limit) return null;
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    private static File diskFile(Context context, String url) {
        File dir = diskDirectory(context);
        if (dir == null) return null;
        return new File(dir, sha256(url) + ".img");
    }

    private static File diskDirectory(Context context) {
        File dir = new File(context.getCacheDir(), DISK_DIR);
        if (dir.isDirectory()) return dir;
        return dir.mkdirs() ? dir : null;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) out.append(String.format(Locale.US, "%02x", b));
            return out.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static void trimDisk(Context context) {
        File dir = diskDirectory(context);
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_DISK_BYTES) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (File file : files) {
            if (total <= MAX_DISK_BYTES) break;
            long length = file.length();
            if (file.delete()) total -= length;
        }
    }

    private static void close(InputStream in) {
        if (in == null) return;
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }

    private static void close(FileOutputStream out) {
        if (out == null) return;
        try {
            out.close();
        } catch (Exception ignored) {
        }
    }
}
