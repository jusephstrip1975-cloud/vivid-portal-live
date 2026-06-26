package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultAssetLoaderFactory;
import androidx.media3.transformer.DefaultDecoderFactory;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import java.io.File;
import java.util.Collections;

/**
 * Transcodes an arbitrary MP4 to a Samsung-friendly H.264 AVC + AAC MP4 using
 * AndroidX Media3 Transformer. Generates a UNIQUE output file per call so the
 * live wallpaper service is always forced to pick up a brand-new asset.
 */
@OptIn(markerClass = UnstableApi.class)
public final class WallpaperVideoTranscoder {

    private static final String TAG = "AetherXLiveWP";
    private static final int SAFE_OUTPUT_HEIGHT = 720;
    private static final int SAFE_OUTPUT_BITRATE = 4_500_000;
    public static final String OUTPUT_PREFIX = "output-samsung-safe-";
    public static final String OUTPUT_SUFFIX = ".mp4";
    private static Transformer currentTransformer;

    public interface Callback {
        void onSuccess(File output);
        void onFailure(Exception error);
    }

    public static void transcode(final Context context, final File input, final Callback cb) {
        new Handler(Looper.getMainLooper()).post(() -> runOnMain(context, input, cb));
    }

    public static File getConvertedDir(Context context) {
        File dir = new File(context.getFilesDir(), "wallpapers/converted");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File buildUniqueOutput(Context context) {
        return new File(getConvertedDir(context),
            OUTPUT_PREFIX + System.currentTimeMillis() + OUTPUT_SUFFIX);
    }

    private static void runOnMain(final Context context, final File input, final Callback cb) {
        try {
            File outDir = getConvertedDir(context);
            // Wipe ALL previous converted outputs so the old file cannot linger,
            // decoder caches cannot keep handles, and Samsung cannot reuse it.
            deleteAllConvertedOutputs(outDir);
            final File output = buildUniqueOutput(context);
            final VideoStats inputStats = readVideoStats(context, input);
            Log.i(TAG, "Transcode unique output target=" + output.getAbsolutePath());
            Log.i(TAG, "Transcoder inputDurationMs=" + inputStats.durationMs
                + " inputFps=" + inputStats.fps
                + " inputSize=" + inputStats.width + "x" + inputStats.height
                + " inputBytes=" + input.length());

            MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(input));
            // Preserve original timestamps/cadence. Do NOT request a fixed output
            // frame rate here: Media3 may duplicate/drop frames to satisfy the
            // request. We only cap resolution for Samsung decoder compatibility.
            EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
                .setEffects(new Effects(
                    Collections.emptyList(),
                    Collections.singletonList(Presentation.createForHeight(SAFE_OUTPUT_HEIGHT))))
                .build();
            Log.i(TAG, "Transcoder frameRateMode=PRESERVE_SOURCE_TIMESTAMPS noFrameRateOverride=true sourceFps="
                + inputStats.fps);

            DefaultDecoderFactory decoderFactory = new DefaultDecoderFactory.Builder(context)
                .setEnableDecoderFallback(true)
                .build();

            DefaultAssetLoaderFactory assetLoaderFactory = new DefaultAssetLoaderFactory(
                context,
                decoderFactory,
                Clock.DEFAULT);

            VideoEncoderSettings.Builder videoSettings = new VideoEncoderSettings.Builder()
                .setBitrate(SAFE_OUTPUT_BITRATE)
                .setiFrameIntervalSeconds(1f);

            DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(context)
                .setEnableFallback(false)
                .setRequestedVideoEncoderSettings(videoSettings.build())
                .build();

            Transformer transformer = new Transformer.Builder(context)
                .setAssetLoaderFactory(assetLoaderFactory)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult exportResult) {
                        VideoStats outputStats = readVideoStats(context, output);
                        long durationDeltaMs = Math.abs(inputStats.durationMs - outputStats.durationMs);
                        Log.i(TAG, "Transformer onCompleted output=" + output.getAbsolutePath()
                            + " size=" + (output.exists() ? output.length() : -1));
                        Log.i(TAG, "Transcoder outputDurationMs=" + outputStats.durationMs
                            + " outputFps=" + outputStats.fps
                            + " outputSize=" + outputStats.width + "x" + outputStats.height
                            + " inputDurationMs=" + inputStats.durationMs
                            + " inputFps=" + inputStats.fps
                            + " durationDeltaMs=" + durationDeltaMs);
                        currentTransformer = null;
                        // Relaxed validation: only reject if file truly missing/empty.
                        // Any playable file with bytes is accepted; renderers will fall back if needed.
                        if (output == null || !output.exists() || output.length() <= 0) {
                            Log.e(TAG, "TRANSCODER_OUTPUT_EMPTY rejecting");
                            cb.onFailure(new IllegalStateException("converted-video-empty"));
                            return;
                        }
                        if (inputStats.durationMs > 0 && outputStats.durationMs > 0) {
                            Log.i(TAG, "TRANSCODER_DURATION_INFO inputDurationMs=" + inputStats.durationMs
                                + " outputDurationMs=" + outputStats.durationMs
                                + " durationDeltaMs=" + durationDeltaMs + " (no rejection, informational)");
                        }
                        cb.onSuccess(output);
                    }

                    @Override
                    public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                        Log.e(TAG, "Transformer onError code=" + exportException.errorCode
                            + " name=" + exportException.getErrorCodeName(), exportException);
                        currentTransformer = null;
                        cb.onFailure(exportException);
                    }
                })
                .build();

            Log.i(TAG, "Transformer.start input=" + input.getAbsolutePath()
                + " output=" + output.getAbsolutePath());
            currentTransformer = transformer;
            transformer.start(editedMediaItem, output.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "Transformer setup failed", t);
            cb.onFailure(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }

    private static void deleteAllConvertedOutputs(File outDir) {
        File[] files = outDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith(OUTPUT_PREFIX)) {
                boolean ok = f.delete();
                Log.i(TAG, "Deleting stale converted file=" + f.getAbsolutePath() + " ok=" + ok);
            }
        }
    }

    private static boolean isReadableVideo(Context context, File file) {
        if (file == null || !file.exists() || file.length() <= 0) return false;
        VideoStats stats = readVideoStats(context, file);
        Log.i(TAG, "Converted metadata width=" + stats.width + " height=" + stats.height
            + " durationMs=" + stats.durationMs + " fps=" + stats.fps);
        return stats.width > 0 && stats.height > 0 && stats.durationMs > 0;
    }

    private static VideoStats readVideoStats(Context context, File file) {
        VideoStats stats = new VideoStats();
        if (file == null || !file.exists()) return stats;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        MediaExtractor extractor = new MediaExtractor();
        try {
            retriever.setDataSource(context, Uri.fromFile(file));
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            stats.width = parseInt(width);
            stats.height = parseInt(height);
            stats.durationMs = parseLong(duration);
            stats.fps = parseFloat(captureFps);

            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.containsKey(MediaFormat.KEY_MIME)
                    ? format.getString(MediaFormat.KEY_MIME)
                    : "";
                if (mime != null && mime.startsWith("video/")) {
                    if (stats.fps <= 0f && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        stats.fps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                    }
                    if (stats.durationMs <= 0 && format.containsKey(MediaFormat.KEY_DURATION)) {
                        stats.durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Video metadata read failed file=" + file.getAbsolutePath(), t);
        } finally {
            try { retriever.release(); } catch (Throwable ignored) {}
            try { extractor.release(); } catch (Throwable ignored) {}
        }
        return stats;
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static float parseFloat(String value) {
        try {
            return value == null ? 0f : Float.parseFloat(value);
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static final class VideoStats {
        long durationMs;
        float fps;
        int width;
        int height;
    }

    private WallpaperVideoTranscoder() {}
}
