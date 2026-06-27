package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.Presentation;
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
 * H.264 (AVC) / 1080x1920 / ~30fps / yuv420p / no audio / faststart.
 */
public final class WallpaperTranscoder {

    private static final String TAG = "AetherXLiveWP";
    private static final long TIMEOUT_SECONDS = 180L;

    /**
     * @return file with normalised wallpaper (overwrites {@code finalOutput}).
     * @throws Exception if transcoding fails or times out.
     */
    public static File transcodeToSamsungSafe(Context ctx, File input, File finalOutput) throws Exception {
        if (input == null || !input.exists()) throw new Exception("transcode-input-missing");
        File tmpOutput = new File(finalOutput.getParentFile(), "current_transcoded.mp4");
        if (tmpOutput.exists() && !tmpOutput.delete()) {
            Log.w(TAG, "TRANSCODE_TMP_DELETE_FAILED path=" + tmpOutput.getAbsolutePath());
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final Handler main = new Handler(Looper.getMainLooper());

        Log.i(TAG, "TRANSCODE_START input=" + input.getAbsolutePath()
            + " tmp=" + tmpOutput.getAbsolutePath()
            + " target=1080x1920@30 H264 noAudio");

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
                                + " videoMime=" + exportResult.videoMimeType);
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

                EditedMediaItem mediaItem = new EditedMediaItem.Builder(
                        MediaItem.fromUri(Uri.fromFile(input)))
                    .setRemoveAudio(true)
                    .setEffects(new Effects(
                        ImmutableList.of(),
                        ImmutableList.of(Presentation.createForWidthAndHeight(
                            WallpaperProbe.TARGET_WIDTH,
                            WallpaperProbe.TARGET_HEIGHT,
                            Presentation.LAYOUT_SCALE_TO_FIT))))
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

        // Atomically replace finalOutput with tmpOutput.
        if (finalOutput.exists() && !finalOutput.delete()) {
            Log.w(TAG, "TRANSCODE_FINAL_DELETE_FAILED path=" + finalOutput.getAbsolutePath());
        }
        if (!tmpOutput.renameTo(finalOutput)) {
            // Fallback: copy + delete
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
            + " size=" + finalOutput.length());
        return finalOutput;
    }
}
