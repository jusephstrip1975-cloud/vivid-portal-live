package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Normalises any downloaded MP4 to Samsung Live Wallpaper safe format:
 * H.264 (AVC) / 1080x1920 PORTRAIT / ~30fps / yuv420p / no audio / faststart.
 *
 * Output MUST be portrait. We ignore the source rotationDegrees metadata and
 * physically rotate the frames so the encoded file has width=1080, height=1920
 * (no rotation flag). Samsung WallpaperService rejects 1920x1080 even when the
 * rotation metadata says portrait.
 */
public final class WallpaperTranscoder {

    private static final String TAG = "AetherXLiveWP";
    private static final long TIMEOUT_SECONDS = 180L;

    public static File transcodeToSamsungSafe(Context ctx, File input, File finalOutput) throws Exception {
        if (input == null || !input.exists()) throw new Exception("transcode-input-missing");

        // Decide rotation by looking at the decoded (post-rotation) frame size from the source.
        WallpaperProbe srcProbe = WallpaperProbe.of(input);
        final int rotationDegrees;
        if (srcProbe.width > 0 && srcProbe.height > 0 && srcProbe.width > srcProbe.height) {
            // Landscape decoded frames -> rotate 90 to get portrait.
            rotationDegrees = 90;
        } else {
            rotationDegrees = 0;
        }
        Log.i(TAG, "TRANSCODE_PLAN srcSize=" + srcProbe.width + "x" + srcProbe.height
            + " applyRotation=" + rotationDegrees
            + " target=1080x1920@30 H264 noAudio portrait");

        File tmpOutput = new File(finalOutput.getParentFile(), "current_transcoded.mp4");
        if (tmpOutput.exists() && !tmpOutput.delete()) {
            Log.w(TAG, "TRANSCODE_TMP_DELETE_FAILED path=" + tmpOutput.getAbsolutePath());
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final Handler main = new Handler(Looper.getMainLooper());

        main.post(() -> {
            try {
                VideoEncoderSettings encoderSettings = new VideoEncoderSettings.Builder()
                    .setBitrate(6_000_000)
                    .build();

                DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(ctx)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .setEnableFallback(true)
                    .build();

                Transformer transformer = new Transformer.Builder(ctx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoderFactory)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition composition, ExportResult exportResult) {
                            Log.i(TAG, "TRANSCODE_COMPLETED durationMs=" + exportResult.durationMs
                                + " videoMime=" + exportResult.videoMimeType
                                + " outW=" + exportResult.width
                                + " outH=" + exportResult.height);
                            latch.countDown();
                        }

                        @Override
                        public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                            Log.e(TAG, "TRANSCODE_ERROR", exportException);
                            errorRef.set(exportException);
                            latch.countDown();
                        }
                    })
                    .build();

                ImmutableList.Builder<androidx.media3.common.Effect> videoEffects = ImmutableList.builder();
                if (rotationDegrees != 0) {
                    // Flatten rotation into frames (bakes orientation, no rotation metadata in output).
                    videoEffects.add(new ScaleAndRotateTransformation.Builder()
                        .setRotationDegrees(rotationDegrees)
                        .build());
                }
                // Force exact portrait 1080x1920. Use SCALE_TO_FIT_WITH_CROP to fully fill the
                // wallpaper frame; LAYOUT_SCALE_TO_FIT can letterbox and some Samsung decoders
                // dislike the resulting non-standard ratio.
                videoEffects.add(Presentation.createForWidthAndHeight(
                    WallpaperProbe.TARGET_WIDTH,
                    WallpaperProbe.TARGET_HEIGHT,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP));

                EditedMediaItem mediaItem = new EditedMediaItem.Builder(
                        MediaItem.fromUri(Uri.fromFile(input)))
                    .setRemoveAudio(true)
                    .setEffects(new Effects(ImmutableList.of(), videoEffects.build()))
                    .build();

                transformer.start(mediaItem, tmpOutput.getAbsolutePath());
            } catch (Throwable t) {
                Log.e(TAG, "TRANSCODE_START_FAILED", t);
                errorRef.set(t);
                latch.countDown();
            }
        });

        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new Exception("transcode-timeout");
        }
        Throwable err = errorRef.get();
        if (err != null) {
            throw new Exception("transcode-failed:" + err.getMessage());
        }
        if (!tmpOutput.exists() || tmpOutput.length() < 1024L * 1024L) {
            throw new Exception("transcode-output-invalid:size=" + (tmpOutput.exists() ? tmpOutput.length() : -1));
        }

        // VALIDATE output orientation BEFORE committing.
        WallpaperProbe outProbe = WallpaperProbe.of(tmpOutput);
        Log.i(TAG, "OUTPUT_PROBE codec=" + outProbe.codec
            + " OUTPUT_WIDTH=" + outProbe.width
            + " OUTPUT_HEIGHT=" + outProbe.height
            + " OUTPUT_FPS=" + outProbe.fps
            + " OUTPUT_HAS_AUDIO=" + outProbe.hasAudio
            + " OUTPUT_ROTATION=0 (flattened)");
        if (outProbe.width <= 0 || outProbe.height <= 0) {
            throw new Exception("FAIL_TRANSCODE_INVALID_PROBE");
        }
        if (outProbe.width > outProbe.height) {
            // Landscape — Samsung will reject. Abort.
            if (tmpOutput.exists() && !tmpOutput.delete()) {
                Log.w(TAG, "TRANSCODE_INVALID_DELETE_FAILED path=" + tmpOutput.getAbsolutePath());
            }
            throw new Exception("FAIL_TRANSCODE_INVALID_ORIENTATION:"
                + outProbe.width + "x" + outProbe.height);
        }
        if (outProbe.width != WallpaperProbe.TARGET_WIDTH
            || outProbe.height != WallpaperProbe.TARGET_HEIGHT) {
            Log.w(TAG, "OUTPUT_SIZE_MISMATCH expected="
                + WallpaperProbe.TARGET_WIDTH + "x" + WallpaperProbe.TARGET_HEIGHT
                + " got=" + outProbe.width + "x" + outProbe.height
                + " (portrait-ok, continuing)");
        }

        // Atomically replace finalOutput with tmpOutput.
        if (finalOutput.exists() && !finalOutput.delete()) {
            Log.w(TAG, "TRANSCODE_FINAL_DELETE_FAILED path=" + finalOutput.getAbsolutePath());
        }
        if (!tmpOutput.renameTo(finalOutput)) {
            java.io.FileInputStream fis = new java.io.FileInputStream(tmpOutput);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(finalOutput, false);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
                fos.getFD().sync();
            } finally {
                try { fis.close(); } catch (Throwable ignored) {}
                try { fos.close(); } catch (Throwable ignored) {}
            }
            if (!tmpOutput.delete()) Log.w(TAG, "TRANSCODE_TMP_CLEANUP_FAILED");
        }
        Log.i(TAG, "TRANSCODE_REPLACED_CURRENT path=" + finalOutput.getAbsolutePath()
            + " size=" + finalOutput.length()
            + " finalSize=" + outProbe.width + "x" + outProbe.height);
        return finalOutput;
    }
}
