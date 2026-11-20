package com.theuncoaffair.fishpipe.download;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import com.theuncoaffair.fishpipe.data.DownloadEntry;
import com.theuncoaffair.fishpipe.net.Http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class SlideshowBuilder {
    public interface Callback {
        boolean isCancelled();

        void progress(long done, long total);
    }

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int FPS = 1;
    private static final long PHOTO_MS = 3000;
    private static final int BITRATE = 6_000_000;
    private static final String MIME_AAC = "audio/mp4a-latm";

    private SlideshowBuilder() {
    }

    public static void build(Context context, DownloadEntry entry, File output, Callback callback)
            throws Exception {
        List<String> urls = new ArrayList<>();
        for (String line : entry.slideshowImages.split("\n")) {
            if (line != null && !line.trim().isEmpty()) urls.add(line.trim());
        }
        if (urls.isEmpty()) throw new IOException("No slideshow images");
        File dir = new File(context.getCacheDir(), "slideshow_" + entry.rowId);
        deleteTree(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("No slideshow cache");
        MediaExtractor audio = null;
        try {
            List<File> images = new ArrayList<>();
            for (int i = 0; i < urls.size(); i++) {
                check(callback);
                File file = new File(dir, "image_" + i);
                download(urls.get(i), file, callback, i, urls.size() + 1);
                images.add(file);
                callback.progress(i + 1, urls.size() + 1);
            }
            File audioFile = null;
            if (entry.slideshowAudio != null && !entry.slideshowAudio.trim().isEmpty()) {
                audioFile = new File(dir, "audio");
                download(entry.slideshowAudio.trim(), audioFile, callback, urls.size(), urls.size() + 1);
                audio = openAudio(audioFile, dir, callback);
            }
            encode(images, audio, output, callback);
        } finally {
            if (audio != null) {
                try {
                    audio.release();
                } catch (Exception ignored) {
                }
            }
            deleteTree(dir);
        }
    }

    private static MediaExtractor openAudio(File source, File dir, Callback callback) throws Exception {
        // Always normalise to AAC in MP4 so the muxer accepts the track regardless
        // of the source container/codec returned by the CDN.
        File aac = new File(dir, "audio_aac.m4a");
        try {
            transcodeToAac(source, aac, callback);
            MediaExtractor out = new MediaExtractor();
            out.setDataSource(aac.getAbsolutePath());
            if (audioTrack(out) >= 0) return out;
            out.release();
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception ignored) {
        }
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(source.getAbsolutePath());
        return extractor;
    }

    private static int audioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    static void transcodeToAac(File input, File output, Callback callback) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        MuxState state = new MuxState();
        try {
            extractor.setDataSource(input.getAbsolutePath());
            int track = audioTrack(extractor);
            if (track < 0) throw new IOException("No audio track");
            MediaFormat inFormat = extractor.getTrackFormat(track);
            String mime = inFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) throw new IOException("No audio mime");
            extractor.selectTrack(track);
            int sampleRate = inFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = inFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inFormat, null, null, 0);
            decoder.start();

            MediaFormat outFormat = MediaFormat.createAudioFormat(MIME_AAC, sampleRate, channels);
            outFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            outFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            outFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536);
            encoder = MediaCodec.createEncoderByType(MIME_AAC);
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(output.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo dinfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo einfo = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean decoderDone = false;
            boolean eosQueued = false;
            byte[] pending = null;
            long pendingTime = 0;

            while (!state.encoderEos) {
                check(callback);
                if (!inputDone && pending == null) {
                    int index = decoder.dequeueInputBuffer(10000);
                    if (index >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(index);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(index, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                if (pending == null && !decoderDone) {
                    int index = decoder.dequeueOutputBuffer(dinfo, 0);
                    if (index >= 0) {
                        if ((dinfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            decoderDone = true;
                        }
                        if (dinfo.size > 0) {
                            ByteBuffer buffer = decoder.getOutputBuffer(index);
                            if (buffer != null) {
                                pending = new byte[dinfo.size];
                                buffer.position(dinfo.offset);
                                buffer.limit(dinfo.offset + dinfo.size);
                                buffer.get(pending);
                                pendingTime = dinfo.presentationTimeUs;
                            }
                        }
                        decoder.releaseOutputBuffer(index, false);
                    }
                }

                if (pending != null) {
                    int index = encoder.dequeueInputBuffer(0);
                    if (index >= 0) {
                        ByteBuffer buffer = encoder.getInputBuffer(index);
                        if (buffer != null) {
                            buffer.clear();
                            buffer.put(pending);
                            encoder.queueInputBuffer(index, 0, pending.length, pendingTime, 0);
                        }
                        pending = null;
                    }
                }

                drainAac(encoder, muxer, einfo, state);

                if (decoderDone && pending == null && !eosQueued) {
                    int index = encoder.dequeueInputBuffer(0);
                    if (index >= 0) {
                        encoder.queueInputBuffer(index, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        eosQueued = true;
                    }
                }
                drainAac(encoder, muxer, einfo, state);
            }
        } finally {
            if (muxer != null) {
                try {
                    if (state.muxerStarted) muxer.stop();
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
            extractor.release();
        }
    }


    private static void encode(List<File> images, MediaExtractor audio, File output, Callback callback)
            throws Exception {
        long audioUs = audioDurationUs(audio);
        long totalUs = Math.max(images.size() * PHOTO_MS * 1000L, audioUs);
        if (totalUs <= 0) totalUs = images.size() * PHOTO_MS * 1000L;
        long perImageUs = Math.max(1L, totalUs / images.size());
        int frameCount = images.size();

        MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
        int color = chooseColor(codec);
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", WIDTH, HEIGHT);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, color);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();

        MediaMuxer muxer = new MediaMuxer(output.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MuxState state = new MuxState();
        int audioExtractorTrack = -1;
        if (audio != null) {
            for (int i = 0; i < audio.getTrackCount(); i++) {
                MediaFormat f = audio.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    state.audioFormat = f;
                    audioExtractorTrack = i;
                    break;
                }
            }
        }

        Bitmap source = null;
        int currentImage = -1;
        Bitmap frame = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        int[] pixels = new int[WIDTH * HEIGHT];
        byte[] yuv = new byte[WIDTH * HEIGHT * 3 / 2];
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean eosQueued = false;
        boolean eosDrained = false;

        try {
            for (int i = 0; i < frameCount && !eosDrained; i++) {
                check(callback);
                int imageIndex = i;
                if (imageIndex != currentImage) {
                    currentImage = imageIndex;
                    if (source != null) source.recycle();
                    source = decode(images.get(imageIndex));
                    canvas.drawColor(Color.BLACK);
                    drawContained(canvas, source, paint);
                }
                frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
                toYuv(pixels, yuv, color);

                int input = codec.dequeueInputBuffer(20000);
                if (input < 0) {
                    i--;
                    drain(codec, muxer, info, state, false);
                    continue;
                }
                ByteBuffer buffer = codec.getInputBuffer(input);
                if (buffer == null) throw new IOException("No encoder input buffer");
                buffer.clear();
                buffer.put(yuv);
                codec.queueInputBuffer(input, 0, yuv.length, i * perImageUs, 0);
                drain(codec, muxer, info, state, false);
                callback.progress(images.size() + 1 + i, images.size() + 1 + frameCount);
            }

            int input = codec.dequeueInputBuffer(20000);
            if (input >= 0) {
                codec.queueInputBuffer(input, 0, 0, frameCount * perImageUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                eosQueued = true;
            }
            while (eosQueued && !eosDrained) {
                eosDrained = drain(codec, muxer, info, state, true);
                check(callback);
            }

            if (audio != null && state.audioTrack >= 0 && audioExtractorTrack >= 0) {
                audio.selectTrack(audioExtractorTrack);
                audio.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                ByteBuffer buf = ByteBuffer.allocateDirect(256 * 1024);
                MediaCodec.BufferInfo ai = new MediaCodec.BufferInfo();
                while (true) {
                    check(callback);
                    buf.clear();
                    int size = audio.readSampleData(buf, 0);
                    if (size < 0) break;
                    ai.set(0, size, audio.getSampleTime(), 0);
                    buf.position(0);
                    buf.limit(size);
                    try {
                        muxer.writeSampleData(state.audioTrack, buf, ai);
                    } catch (Exception ignored) {
                        break;
                    }
                    audio.advance();
                }
            }
        } finally {
            if (source != null) source.recycle();
            frame.recycle();
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            codec.release();
            if (state.muxerStarted) {
                try {
                    muxer.stop();
                } catch (Exception ignored) {
                }
            }
            muxer.release();
        }
    }

    private static void drainAac(MediaCodec encoder, MediaMuxer muxer,
                                 MediaCodec.BufferInfo info, MuxState state) {
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, 0);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!state.muxerStarted) {
                    state.audioTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    state.muxerStarted = true;
                }
                continue;
            }
            if (index < 0) return;
            ByteBuffer out = encoder.getOutputBuffer(index);
            if (info.size > 0 && state.muxerStarted && out != null) {
                out.position(info.offset);
                out.limit(info.offset + info.size);
                muxer.writeSampleData(state.audioTrack, out, info);
            }
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            encoder.releaseOutputBuffer(index, false);
            if (eos) {
                state.encoderEos = true;
                return;
            }
        }
    }

    private static boolean drain(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                                 MuxState state, boolean end) throws IOException {
        while (true) {
            int index = codec.dequeueOutputBuffer(info, end ? 10000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!end) return false;
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (state.muxerStarted) throw new IOException("Encoder format changed twice");
                MediaFormat out = codec.getOutputFormat();
                state.videoTrack = muxer.addTrack(out);
                if (state.audioFormat != null && !state.audioAdded) {
                    try {
                        state.audioTrack = muxer.addTrack(state.audioFormat);
                        state.audioAdded = true;
                    } catch (Exception ignored) {
                        state.audioTrack = -1;
                    }
                }
                muxer.start();
                state.muxerStarted = true;
                continue;
            }
            if (index < 0) continue;
            ByteBuffer out = codec.getOutputBuffer(index);
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
            if (info.size > 0 && state.muxerStarted && out != null) {
                out.position(info.offset);
                out.limit(info.offset + info.size);
                muxer.writeSampleData(state.videoTrack, out, info);
            }
            boolean done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            codec.releaseOutputBuffer(index, false);
            if (done) return true;
        }
    }

    private static final class MuxState {
        int videoTrack = -1;
        int audioTrack = -1;
        boolean muxerStarted;
        boolean audioAdded;
        boolean encoderEos;
        MediaFormat audioFormat;
    }

    private static int chooseColor(MediaCodec codec) {
        MediaCodecInfo info = codec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("video/avc");
        for (int c : caps.colorFormats) {
            if (c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) return c;
        }
        for (int c : caps.colorFormats) {
            if (c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) return c;
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private static void toYuv(int[] argb, byte[] out, int color) {
        int ySize = WIDTH * HEIGHT;
        int uvWidth = WIDTH / 2;
        boolean semi = color != MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
        int uOffset = ySize;
        int vOffset = ySize + ySize / 4;
        for (int j = 0; j < HEIGHT; j++) {
            int row = j * WIDTH;
            for (int i = 0; i < WIDTH; i++) {
                int c = argb[row + i];
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                out[row + i] = (byte) (y < 0 ? 0 : Math.min(255, y));
                if ((j & 1) == 0 && (i & 1) == 0) {
                    int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    u = u < 0 ? 0 : Math.min(255, u);
                    v = v < 0 ? 0 : Math.min(255, v);
                    int pos = (j / 2) * uvWidth + (i / 2);
                    if (semi) {
                        out[uOffset + pos * 2] = (byte) u;
                        out[uOffset + pos * 2 + 1] = (byte) v;
                    } else {
                        out[uOffset + pos] = (byte) u;
                        out[vOffset + pos] = (byte) v;
                    }
                }
            }
        }
    }

    private static long audioDurationUs(MediaExtractor audio) {
        if (audio == null) return 0;
        for (int i = 0; i < audio.getTrackCount(); i++) {
            MediaFormat f = audio.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("audio/")) continue;
            if (f.containsKey(MediaFormat.KEY_DURATION)) return f.getLong(MediaFormat.KEY_DURATION);
        }
        return 0;
    }

    private static Bitmap decode(File file) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Bad image");
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > 2160) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (bitmap == null) throw new IOException("Bad image");
        return bitmap;
    }

    private static void drawContained(Canvas canvas, Bitmap bitmap, Paint paint) {
        float scale = Math.min((float) WIDTH / bitmap.getWidth(), (float) HEIGHT / bitmap.getHeight());
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        float left = (WIDTH - w) / 2f;
        float top = (HEIGHT - h) / 2f;
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + w, top + h), paint);
    }

    private static void download(String url, File output, Callback callback, long done, long total)
            throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", Http.MOBILE_UA);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Referer", "https://www.tiktok.com/");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            in = conn.getInputStream();
            out = new FileOutputStream(output);
            byte[] buffer = new byte[64 * 1024];
            long bytes = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                check(callback);
                out.write(buffer, 0, n);
                bytes += n;
                callback.progress(done, total);
            }
            out.flush();
            if (bytes == 0) throw new IOException("Empty response");
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (conn != null) conn.disconnect();
        }
    }

    private static void check(Callback callback) throws InterruptedException {
        if (callback != null && callback.isCancelled()) throw new InterruptedException("cancelled");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
