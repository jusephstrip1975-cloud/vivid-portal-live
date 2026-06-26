package com.aetherx.localfinal.wallpaper;

import android.content.Context;
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
    private static final int SAFE_OUTPUT_FRAME_RATE = 30;
    private static final int SAFE_OUTPUT_BITRATE = 1_800_000;
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
            Log.i(TAG, "Transcode unique output target=" + output.getAbsolutePath());

            MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(input));
            EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
                .setFrameRate(SAFE_OUTPUT_FRAME_RATE)
                .setEffects(new Effects(
                    Collections.emptyList(),
                    Collections.singletonList(Presentation.createForHeight(SAFE_OUTPUT_HEIGHT))))
                .build();

            DefaultDecoderFactory decoderFactory = new DefaultDecoderFactory.Builder(context)
                .setEnableDecoderFallback(true)
                .build();

            DefaultAssetLoaderFactory assetLoaderFactory = new DefaultAssetLoaderFactory(
                context,
                decoderFactory,
                Clock.DEFAULT);

            DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(context)
                .setEnableFallback(false)
                .setRequestedVideoEncoderSettings(
                    new VideoEncoderSettings.Builder()
                        .setBitrate(SAFE_OUTPUT_BITRATE)
                        .setiFrameIntervalSeconds(1f)
                        .build())
                .build();

            Transformer transformer = new Transformer.Builder(context)
                .setAssetLoaderFactory(assetLoaderFactory)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult exportResult) {
                        Log.i(TAG, "Transformer onCompleted output=" + output.getAbsolutePath()
                            + " size=" + (output.exists() ? output.length() : -1));
                        currentTransformer = null;
                        if (isReadableVideo(context, output)) {
                            cb.onSuccess(output);
                        } else {
                            cb.onFailure(new IllegalStateException("converted-video-not-readable"));
                        }
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
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.fromFile(file));
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            Log.i(TAG, "Converted metadata width=" + width + " height=" + height + " duration=" + duration);
            return parsePositive(width) && parsePositive(height) && parsePositive(duration);
        } catch (Throwable t) {
            Log.e(TAG, "Converted metadata validation failed", t);
            return false;
        } finally {
            try { retriever.release(); } catch (Throwable ignored) {}
        }
    }

    private static boolean parsePositive(String value) {
        try {
            return value != null && Long.parseLong(value) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private WallpaperVideoTranscoder() {}
}
