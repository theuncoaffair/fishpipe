package com.theuncoaffair.fishpipe.download;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.provider.MediaStore;

import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.net.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CaptionBuilder {
    public interface Callback {
        boolean isCancelled();

        void progress(long done, long total);
    }

    private static final String MIME_AAC = "audio/mp4a-latm";

    private CaptionBuilder() {
    }

    public static List<Cue> load(DownloadEntry entry) throws Exception {
        if (entry == null || entry.captionUrl == null || entry.captionUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", Http.MOBILE_UA);
        headers.put("Referer", "https://www.tiktok.com/");
        headers.put("Accept", "text/vtt,application/json,text/plain,*/*");
        Http.Response response = Http.get(entry.captionUrl.trim(), headers);
        if (!response.ok()) throw new IOException("Caption HTTP " + response.code);
        return parse(response.body);
    }

    public static List<Cue> loadSidecar(Context context, DownloadEntry entry) {
        List<Cue> cues = readSidecar(context, entry);
        if (!cues.isEmpty()) return cues;
        if (entry == null || entry.captionUrl == null || entry.captionUrl.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return load(entry);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static boolean hasSidecar(Context context, DownloadEntry entry) {
        return !readSidecar(context, entry).isEmpty();
    }

    private static List<Cue> readSidecar(Context context, DownloadEntry entry) {
        if (context == null || entry == null || entry.localUri == null) {
            return Collections.emptyList();
        }
        String display = Storage.displayName(context, entry.localUri);
        if (display.trim().isEmpty()) return Collections.emptyList();
        String srtName = baseName(display) + ".srt";
        Uri local = Uri.parse(entry.localUri);
        if ("file".equals(local.getScheme()) || entry.localUri.startsWith("/")) {
            String path = local.getPath() == null ? entry.localUri : local.getPath();
            File file = new File(path);
            File srt = file.getParentFile() == null ? null
                    : new File(file.getParentFile(), srtName);
            if (srt != null && srt.isFile()) {
                try {
                    return parseText(read(srt));
                } catch (Exception ignored) {
                }
            }
        }
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri("external");
        String[] projection = {MediaStore.MediaColumns._ID};
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        try (Cursor cursor = resolver.query(collection, projection, selection,
                new String[]{srtName}, null)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
                while (cursor.moveToNext()) {
                    if (idColumn < 0) continue;
                    Uri uri = Uri.withAppendedPath(collection,
                            String.valueOf(cursor.getLong(idColumn)));
                    try (InputStream in = resolver.openInputStream(uri)) {
                        if (in == null) continue;
                        List<Cue> cues = parseText(read(in));
                        if (!cues.isEmpty()) return cues;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return read(in);
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static void writeSrt(DownloadEntry entry, File output, Callback callback) throws Exception {
        List<Cue> cues = load(entry);
        if (cues.isEmpty()) throw new IOException("No caption cues");
        check(callback);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(output),
                StandardCharsets.UTF_8)) {
            for (int i = 0; i < cues.size(); i++) {
                check(callback);
                Cue cue = cues.get(i);
                writer.write(Integer.toString(i + 1));
                writer.write('\n');
                writer.write(stamp(cue.startMs));
                writer.write(" --> ");
                writer.write(stamp(cue.endMs));
                writer.write('\n');
                writer.write(cue.text);
                writer.write("\n\n");
            }
        }
    }

    public static boolean burnIn(DownloadEntry entry, File input, File output,
                                 Callback callback) throws Exception {
        List<Cue> cues = load(entry);
        if (cues.isEmpty()) return false;
        burn(input, output, cues, callback);
        return output.isFile() && output.length() > 0;
    }

    public static boolean burnSidecar(Context context, DownloadEntry entry, File input, File output,
                                      Callback callback) throws Exception {
        List<Cue> cues = readSidecar(context, entry);
        if (cues.isEmpty()) return false;
        burn(input, output, cues, callback);
        return output.isFile() && output.length() > 0;
    }

    private static void burn(File input, File output, List<Cue> cues, Callback callback)
            throws Exception {
        if (output.exists() && !output.delete()) throw new IOException("Could not replace caption video");
        MediaExtractor video = new MediaExtractor();
        MediaExtractor audio = null;
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        File audioTemp = null;
        MuxState state = new MuxState();
        try {
            video.setDataSource(input.getAbsolutePath());
            int videoTrack = track(video, "video/");
            if (videoTrack < 0) throw new IOException("No video track");
            video.selectTrack(videoTrack);
            MediaFormat videoFormat = video.getTrackFormat(videoTrack);
            String videoMime = videoFormat.getString(MediaFormat.KEY_MIME);
            if (videoMime == null) throw new IOException("No video mime");
            int sourceWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
            int sourceHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            int rotation = videoFormat.containsKey(MediaFormat.KEY_ROTATION)
                    ? videoFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
            int fps = videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)
                    ? videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) : 30;
            if (fps < 12 || fps > 60) fps = 30;
            boolean swap = rotation == 90 || rotation == 270;
            int outWidth = even(swap ? sourceHeight : sourceWidth);
            int outHeight = even(swap ? sourceWidth : sourceHeight);
            if (outWidth <= 0 || outHeight <= 0) throw new IOException("Bad video size");

            audio = new MediaExtractor();
            audio.setDataSource(input.getAbsolutePath());
            int audioTrack = track(audio, "audio/");
            MediaFormat audioFormat = null;
            if (audioTrack >= 0) {
                audio.selectTrack(audioTrack);
                audioFormat = audio.getTrackFormat(audioTrack);
                String audioMime = audioFormat.getString(MediaFormat.KEY_MIME);
                if (audioMime == null || !MIME_AAC.equals(audioMime)) {
                    audioTemp = File.createTempFile("caption_audio_", ".m4a", input.getParentFile());
                    try {
                        SlideshowBuilder.transcodeToAac(input, audioTemp,
                                new SlideshowBuilder.Callback() {
                                    @Override
                                    public boolean isCancelled() {
                                        return callback != null && callback.isCancelled();
                                    }

                                    @Override
                                    public void progress(long done, long total) {
                                        if (callback != null) callback.progress(done, total);
                                    }
                                });
                        audio.release();
                        audio = new MediaExtractor();
                        audio.setDataSource(audioTemp.getAbsolutePath());
                        audioTrack = track(audio, "audio/");
                        if (audioTrack >= 0) {
                            audio.selectTrack(audioTrack);
                            audioFormat = audio.getTrackFormat(audioTrack);
                        } else {
                            audioFormat = null;
                        }
                    } catch (Exception e) {
                        audioFormat = null;
                    }
                }
            }
            if (audioTrack >= 0 && audioFormat == null) {
                throw new IOException("Audio track could not be prepared");
            }

            decoder = MediaCodec.createDecoderByType(videoMime);
            decoder.configure(videoFormat, null, null, 0);
            decoder.start();

            encoder = MediaCodec.createEncoderByType("video/avc");
            int color = chooseColor(encoder);
            MediaFormat encodeFormat = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight);
            encodeFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color);
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate(outWidth, outHeight, fps));
            encodeFormat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            encodeFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(output.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            state.audioFormat = audioFormat;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] yuv = null;
            int[] pixels = new int[outWidth * outHeight];
            Bitmap renderBitmap = null;
            boolean inputDone = false;
            boolean decoderDone = false;
            boolean eosQueued = false;
            long lastUs = 0;
            int[] cueCursor = {0};
            long totalUs = videoFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? videoFormat.getLong(MediaFormat.KEY_DURATION) : 0L;

            while (!state.encoderEos) {
                check(callback);
                if (!inputDone) {
                    int index = decoder.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(index);
                        int size = buffer == null ? -1 : video.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(index, 0, size, video.getSampleTime(), 0);
                            video.advance();
                        }
                    }
                }

                if (!decoderDone) {
                    int index = decoder.dequeueOutputBuffer(info, 0);
                    if (index >= 0) {
                        boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        Image image = decoder.getOutputImage(index);
                        if (image != null) {
                            long timeMs = info.presentationTimeUs / 1000L;
                            Cue cue = active(cues, timeMs, cueCursor);
                            int size = outWidth * outHeight * 3 / 2;
                            if (yuv == null || yuv.length != size) yuv = new byte[size];
                            boolean direct = cue == null && !swap
                                    && image.getWidth() == outWidth
                                    && image.getHeight() == outHeight
                                    && image.getFormat() == ImageFormat.YUV_420_888;
                            if (direct) {
                                imageToYuv(image, yuv, outWidth, outHeight, color);
                                image.close();
                            } else {
                                boolean reusable = !swap
                                        && image.getWidth() == outWidth
                                        && image.getHeight() == outHeight
                                        && image.getFormat() == ImageFormat.YUV_420_888;
                                Bitmap bitmap;
                                if (reusable) {
                                    if (renderBitmap == null) {
                                        renderBitmap = Bitmap.createBitmap(outWidth, outHeight,
                                                Bitmap.Config.ARGB_8888);
                                    }
                                    imageToBitmap(image, renderBitmap, pixels);
                                    bitmap = renderBitmap;
                                } else {
                                    bitmap = imageToBitmap(image);
                                }
                                image.close();
                                if (swap) bitmap = rotate(bitmap, rotation);
                                if (bitmap.getWidth() != outWidth || bitmap.getHeight() != outHeight) {
                                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, outWidth, outHeight,
                                            true);
                                    if (scaled != bitmap) bitmap.recycle();
                                    bitmap = scaled;
                                    reusable = false;
                                }
                                RENDERER.draw(bitmap, cue);
                                bitmap.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight);
                                if (!reusable) bitmap.recycle();
                                toYuv(pixels, yuv, outWidth, outHeight, color);
                            }
                            long ptsUs = info.presentationTimeUs;
                            encodeFrame(encoder, yuv, ptsUs, info, muxer, state);
                            lastUs = ptsUs;
                            if (callback != null && totalUs > 0) {
                                callback.progress(Math.min(ptsUs, totalUs), totalUs);
                            }
                        }
                        decoder.releaseOutputBuffer(index, false);
                        if (eos) decoderDone = true;
                    }
                }

                drainEncoder(encoder, muxer, info, state);
                if (decoderDone && !eosQueued) {
                    int index = encoder.dequeueInputBuffer(0);
                    if (index >= 0) {
                        encoder.queueInputBuffer(index, 0, 0, lastUs + 1_000_000L / fps,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eosQueued = true;
                    }
                }
                drainEncoder(encoder, muxer, info, state);
            }

            if (audioTrack >= 0 && state.audioTrack >= 0 && state.started) {
                audio.selectTrack(audioTrack);
                audio.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                ByteBuffer buffer = ByteBuffer.allocateDirect(256 * 1024);
                MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
                while (true) {
                    check(callback);
                    buffer.clear();
                    int size = audio.readSampleData(buffer, 0);
                    if (size < 0) break;
                    audioInfo.set(0, size, audio.getSampleTime(), 0);
                    buffer.position(0);
                    buffer.limit(size);
                    try {
                        muxer.writeSampleData(state.audioTrack, buffer, audioInfo);
                    } catch (Exception ignored) {
                        break;
                    }
                    audio.advance();
                }
            }
        } finally {
            if (muxer != null) {
                try {
                    if (state.started) muxer.stop();
                } catch (Exception ignored) {
                }
                muxer.release();
            }
            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (Exception ignored) {
                }
                encoder.release();
            }
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception ignored) {
                }
                decoder.release();
            }
            if (audio != null) audio.release();
            video.release();
            if (audioTemp != null) {
                //noinspection ResultOfMethodCallIgnored
                audioTemp.delete();
            }
        }
    }

    private static void encodeFrame(MediaCodec encoder, byte[] yuv, long timeUs,
                                    MediaCodec.BufferInfo info, MediaMuxer muxer, MuxState state)
            throws IOException {
        while (true) {
            int index = encoder.dequeueInputBuffer(20000);
            if (index >= 0) {
                ByteBuffer buffer = encoder.getInputBuffer(index);
                if (buffer == null) throw new IOException("No encoder input buffer");
                buffer.clear();
                buffer.put(yuv);
                encoder.queueInputBuffer(index, 0, yuv.length, timeUs, 0);
                return;
            }
            drainEncoder(encoder, muxer, info, state);
        }
    }

    private static void drainEncoder(MediaCodec encoder, MediaMuxer muxer,
                                     MediaCodec.BufferInfo info, MuxState state)
            throws IOException {
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, 0);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (state.started) throw new IOException("Encoder format changed twice");
                state.videoTrack = muxer.addTrack(encoder.getOutputFormat());
                if (state.audioFormat != null && !state.audioAdded) {
                    try {
                        state.audioTrack = muxer.addTrack(state.audioFormat);
                        state.audioAdded = true;
                    } catch (Exception e) {
                        throw new IOException("Audio track rejected", e);
                    }
                }
                muxer.start();
                state.started = true;
                continue;
            }
            if (index < 0) return;
            ByteBuffer out = encoder.getOutputBuffer(index);
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
            if (info.size > 0 && state.started && out != null) {
                out.position(info.offset);
                out.limit(info.offset + info.size);
                muxer.writeSampleData(state.videoTrack, out, info);
            }
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            encoder.releaseOutputBuffer(index, false);
            if (eos) {
                state.encoderEos = true;
                return;
            }
        }
    }

    private static Bitmap imageToBitmap(Image image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) throw new IOException("Bad decoded image");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        imageToBitmap(image, bitmap, new int[width * height]);
        return bitmap;
    }

    private static void imageToBitmap(Image image, Bitmap bitmap, int[] pixels) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) throw new IOException("Bad decoded image");
        if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
            throw new IOException("Bitmap size mismatch");
        }
        if (pixels.length < width * height) throw new IOException("Pixel buffer too small");
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3) throw new IOException("Unsupported image format");
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int yRow = planes[0].getRowStride();
        int yPixel = planes[0].getPixelStride();
        int uRow = planes[1].getRowStride();
        int uPixel = planes[1].getPixelStride();
        int vRow = planes[2].getRowStride();
        int vPixel = planes[2].getPixelStride();
        int p = 0;
        for (int row = 0; row < height; row++) {
            int yBase = row * yRow;
            int uvRow = row / 2;
            int uBase = uvRow * uRow;
            int vBase = uvRow * vRow;
            for (int col = 0; col < width; col++) {
                int y = (yBuffer.get(yBase + col * yPixel) & 0xff) - 16;
                if (y < 0) y = 0;
                int uvCol = col / 2;
                int u = (uBuffer.get(uBase + uvCol * uPixel) & 0xff) - 128;
                int v = (vBuffer.get(vBase + uvCol * vPixel) & 0xff) - 128;
                int r = (298 * y + 409 * v + 128) >> 8;
                int g = (298 * y - 100 * u - 208 * v + 128) >> 8;
                int b = (298 * y + 516 * u + 128) >> 8;
                r = r < 0 ? 0 : Math.min(255, r);
                g = g < 0 ? 0 : Math.min(255, g);
                b = b < 0 ? 0 : Math.min(255, b);
                pixels[p++] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static void imageToYuv(Image image, byte[] out, int width, int height, int color)
            throws IOException {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length < 3) throw new IOException("Unsupported image format");
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int yRow = planes[0].getRowStride();
        int yPixel = planes[0].getPixelStride();
        int uRow = planes[1].getRowStride();
        int uPixel = planes[1].getPixelStride();
        int vRow = planes[2].getRowStride();
        int vPixel = planes[2].getPixelStride();
        for (int row = 0; row < height; row++) {
            int yBase = row * yRow;
            int outBase = row * width;
            for (int col = 0; col < width; col++) {
                out[outBase + col] = yBuffer.get(yBase + col * yPixel);
            }
        }
        int ySize = width * height;
        int uvWidth = width / 2;
        boolean semi = color != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        int uOffset = ySize;
        int vOffset = ySize + ySize / 4;
        for (int row = 0; row < height / 2; row++) {
            int uBase = row * uRow;
            int vBase = row * vRow;
            for (int col = 0; col < uvWidth; col++) {
                byte u = uBuffer.get(uBase + col * uPixel);
                byte v = vBuffer.get(vBase + col * vPixel);
                int position = row * uvWidth + col;
                if (semi) {
                    out[uOffset + position * 2] = u;
                    out[uOffset + position * 2 + 1] = v;
                } else {
                    out[uOffset + position] = u;
                    out[vOffset + position] = v;
                }
            }
        }
    }

    private static Bitmap rotate(Bitmap source, int degrees) {
        if (degrees == 0) return source;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(),
                source.getHeight(), matrix, true);
        if (rotated != source) source.recycle();
        return rotated;
    }

    private static final CaptionRenderer RENDERER = new CaptionRenderer();

    private static final class CaptionRenderer {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Canvas canvas = new Canvas();
        private int width;
        private int height;
        private float textSize;
        private String cachedText;
        private List<String> cachedLines;

        CaptionRenderer() {
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            background.setStyle(Paint.Style.FILL);
            background.setColor(0x99000000);
        }

        void draw(Bitmap bitmap, Cue cue) {
            if (cue == null || cue.text.isEmpty()) return;
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w != width || h != height) {
                width = w;
                height = h;
                textSize = Math.max(18f, w * 0.036f);
                paint.setTextSize(textSize);
                paint.setShadowLayer(textSize * 0.10f, 0f, textSize * 0.04f, 0xCC000000);
                cachedText = null;
                cachedLines = null;
            }
            if (!cue.text.equals(cachedText)) {
                cachedText = cue.text;
                cachedLines = wrap(cue.text, paint, width * 0.86f);
            }
            List<String> lines = cachedLines;
            if (lines == null || lines.isEmpty()) return;
            float lineHeight = textSize * 1.2f;
            float baseline = height * 0.86f - (lines.size() - 1) * lineHeight;
            float maxLine = 0f;
            for (String line : lines) maxLine = Math.max(maxLine, paint.measureText(line));
            float padX = textSize * 0.75f;
            float padY = textSize * 0.35f;
            float left = width / 2f - maxLine / 2f - padX;
            float right = width / 2f + maxLine / 2f + padX;
            float top = baseline - textSize - padY;
            float bottom = baseline + (lines.size() - 1) * lineHeight + padY;
            canvas.setBitmap(bitmap);
            canvas.drawRect(left, top, right, bottom, background);
            for (int i = 0; i < lines.size(); i++) {
                canvas.drawText(lines.get(i), width / 2f, baseline + i * lineHeight, paint);
            }
            canvas.setBitmap(null);
        }
    }

    private static Cue active(List<Cue> cues, long timeMs, int[] cursor) {
        int i = cursor[0];
        while (i < cues.size() && cues.get(i).endMs <= timeMs) i++;
        cursor[0] = i;
        if (i >= cues.size()) return null;
        Cue cue = cues.get(i);
        return timeMs >= cue.startMs && timeMs < cue.endMs ? cue : null;
    }

    private static List<String> wrap(String text, Paint paint, float maxWidth) {
        String clean = text.replace('\n', ' ').replace('\r', ' ').trim();
        List<String> lines = new ArrayList<>();
        if (clean.isEmpty()) return lines;
        String[] words = clean.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private static List<Cue> parse(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) return Collections.emptyList();
        List<Cue> cues;
        if (text.startsWith("{") || text.startsWith("[")) {
            cues = parseJson(text);
        } else {
            cues = parseText(text);
        }
        Collections.sort(cues, new Comparator<Cue>() {
            @Override
            public int compare(Cue left, Cue right) {
                return Long.compare(left.startMs, right.startMs);
            }
        });
        return cues;
    }

    private static List<Cue> parseJson(String text) {
        List<Cue> cues = new ArrayList<>();
        try {
            Object root = text.startsWith("[") ? new JSONArray(text) : new JSONObject(text);
            collectJson(root, cues, 0);
        } catch (Exception ignored) {
        }
        return cues;
    }

    private static void collectJson(Object node, List<Cue> cues, int depth) {
        if (node == null || depth > 8) return;
        if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                Object child = array.opt(i);
                if (child instanceof JSONObject) {
                    Cue cue = cue((JSONObject) child);
                    if (cue != null) cues.add(cue);
                    else collectJson(child, cues, depth + 1);
                } else if (child instanceof JSONArray) {
                    collectJson(child, cues, depth + 1);
                }
            }
            return;
        }
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            Cue cue = cue(object);
            if (cue != null) {
                cues.add(cue);
                return;
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                Object child = object.opt(keys.next());
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    collectJson(child, cues, depth + 1);
                }
            }
        }
    }

    private static Cue cue(JSONObject object) {
        String value = first(object, "content", "text", "caption", "sentence", "utf8", "value");
        if (value == null || value.trim().isEmpty()) {
            JSONArray words = object.optJSONArray("words");
            if (words != null) {
                StringBuilder joined = new StringBuilder();
                for (int i = 0; i < words.length(); i++) {
                    Object word = words.opt(i);
                    if (word instanceof JSONObject) {
                        String part = first((JSONObject) word, "word", "text", "content");
                        if (part != null && !part.trim().isEmpty()) {
                            if (joined.length() > 0) joined.append(' ');
                            joined.append(part.trim());
                        }
                    }
                }
                value = joined.toString();
            }
        }
        if (value == null || value.trim().isEmpty()) return null;
        boolean milliseconds = object.has("tStartMs") || object.has("dDurationMs")
                || object.has("startTime") || object.has("endTime");
        Double start = time(object, milliseconds,
                "from", "start", "startTime", "start_time", "tStartMs", "begin");
        Double end = time(object, milliseconds,
                "to", "end", "endTime", "end_time", "tEndMs");
        if (start == null) return null;
        if (end == null) {
            Double duration = time(object, milliseconds, "duration", "dDurationMs", "dur");
            if (duration != null) end = start + duration;
        }
        if (end == null || end <= start) return null;
        Cue cue = new Cue();
        cue.startMs = Math.max(0, Math.round(start));
        cue.endMs = Math.max(cue.startMs + 1, Math.round(end));
        cue.text = clean(value);
        return cue;
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) continue;
            String value = object.optString(key, "");
            if (!value.trim().isEmpty()) return value;
        }
        return null;
    }

    private static Double time(JSONObject object, boolean milliseconds, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.isNull(key)) continue;
            double value = object.optDouble(key, Double.NaN);
            if (Double.isNaN(value)) continue;
            String lower = key.toLowerCase(Locale.US);
            if (lower.contains("ms") || milliseconds) return value;
            return value * 1000d;
        }
        return null;
    }

    private static List<Cue> parseText(String text) {
        List<Cue> cues = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\\n\\s*\\n");
        for (String block : blocks) {
            String[] lines = block.split("\n");
            int timing = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timing = i;
                    break;
                }
            }
            if (timing < 0) continue;
            String[] parts = lines[timing].split("-->");
            if (parts.length < 2) continue;
            Long start = timestamp(parts[0]);
            Long end = timestamp(parts[1].trim().split("\\s+")[0]);
            if (start == null || end == null || end <= start) continue;
            StringBuilder content = new StringBuilder();
            for (int i = timing + 1; i < lines.length; i++) {
                if (content.length() > 0) content.append('\n');
                content.append(lines[i]);
            }
            Cue cue = new Cue();
            cue.startMs = start;
            cue.endMs = end;
            cue.text = clean(content.toString());
            if (!cue.text.isEmpty()) cues.add(cue);
        }
        return cues;
    }

    private static Long timestamp(String value) {
        String clean = value.trim().replace(',', '.');
        String[] parts = clean.split(":");
        try {
            double seconds;
            if (parts.length == 3) {
                seconds = Double.parseDouble(parts[0]) * 3600d
                        + Double.parseDouble(parts[1]) * 60d
                        + Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                seconds = Double.parseDouble(parts[0]) * 60d + Double.parseDouble(parts[1]);
            } else {
                seconds = Double.parseDouble(parts[0]);
            }
            return Math.round(seconds * 1000d);
        } catch (Exception e) {
            return null;
        }
    }

    private static String clean(String value) {
        return value.replaceAll("<[^>]*>", "")
                .replaceAll("\\{[^}]*\\}", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .trim();
    }

    private static String stamp(long ms) {
        long total = Math.max(0, ms);
        long hours = total / 3_600_000L;
        total %= 3_600_000L;
        long minutes = total / 60_000L;
        total %= 60_000L;
        long seconds = total / 1000L;
        long millis = total % 1000L;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }

    private static int track(MediaExtractor extractor, String prefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }

    private static int even(int value) {
        return value <= 1 ? value : (value & ~1);
    }

    private static int bitrate(int width, int height, int fps) {
        long pixels = (long) width * height;
        int rate = (int) Math.min(12_000_000L, Math.max(2_000_000L, pixels * fps / 8L));
        return rate;
    }

    private static int chooseColor(MediaCodec codec) {
        MediaCodecInfo info = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("video/avc");
        for (int color : caps.colorFormats) {
            if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                return color;
            }
        }
        for (int color : caps.colorFormats) {
            if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                return color;
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private static void toYuv(int[] argb, byte[] out, int width, int height, int color) {
        int ySize = width * height;
        int uvWidth = width / 2;
        boolean semi = color != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        int uOffset = ySize;
        int vOffset = ySize + ySize / 4;
        for (int row = 0; row < height; row++) {
            int rowStart = row * width;
            for (int col = 0; col < width; col++) {
                int value = argb[rowStart + col];
                int r = (value >> 16) & 0xff;
                int g = (value >> 8) & 0xff;
                int b = value & 0xff;
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                out[rowStart + col] = (byte) (y < 0 ? 0 : Math.min(255, y));
                if ((row & 1) == 0 && (col & 1) == 0) {
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    u = u < 0 ? 0 : Math.min(255, u);
                    v = v < 0 ? 0 : Math.min(255, v);
                    int position = (row / 2) * uvWidth + (col / 2);
                    if (semi) {
                        out[uOffset + position * 2] = (byte) u;
                        out[uOffset + position * 2 + 1] = (byte) v;
                    } else {
                        out[uOffset + position] = (byte) u;
                        out[vOffset + position] = (byte) v;
                    }
                }
            }
        }
    }

    private static void check(Callback callback) throws InterruptedException {
        if (callback != null && callback.isCancelled()) throw new InterruptedException("Cancelled");
    }

    private static final class MuxState {
        int videoTrack = -1;
        int audioTrack = -1;
        boolean started;
        boolean audioAdded;
        boolean encoderEos;
        MediaFormat audioFormat;
    }

    public static final class Cue {
        public long startMs;
        public long endMs;
        public String text = "";
    }
}
