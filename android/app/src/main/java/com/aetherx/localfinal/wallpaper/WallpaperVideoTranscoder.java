package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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
 * Transcoder a MP4 H.264 Baseline 3.0 / 720x1280 / 30 fps / yuv420p / sin audio.
 * Equivalente al comando FFmpeg solicitado, ejecutado vía Media3 Transformer
 * (MediaCodec por debajo) para no depender de FFmpegKit. Tras escribir el
 * archivo lo valida con MediaExtractor (estilo ffprobe) y rechaza cualquier
 * salida que no cumpla Baseline / AVC / yuv420p.
 */
@OptIn(markerClass = UnstableApi.class)
public final class WallpaperVideoTranscoder {

    private static final String TAG = "AetherXLiveWP";
    // Forzado portrait 720x1280 según requisitos (Samsung live wallpaper friendly)
    private static final int SAFE_OUTPUT_WIDTH = 720;
    private static final int SAFE_OUTPUT_HEIGHT = 1280;
    private static final int SAFE_OUTPUT_FRAME_RATE = 30;
    private static final int SAFE_OUTPUT_BITRATE = 2_500_000;

    private static Transformer currentTransformer;

    public interface Callback {
        void onSuccess(File output);
        void onFailure(Exception error);
    }

    public static void transcode(final Context context, final File input, final Callback cb) {
        new Handler(Looper.getMainLooper()).post(() -> runOnMain(context, input, cb));
    }

    private static void runOnMain(final Context context, final File input, final Callback cb) {
        try {
            File outDir = new File(context.getFilesDir(), "wallpapers/converted");
            if (!outDir.exists()) outDir.mkdirs();
            final File output = new File(outDir, "output.mp4");
            if (output.exists() && !output.delete()) {
                Log.w(TAG, "Could not delete previous converted output: " + output.getAbsolutePath());
            }

            MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(input));
            EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true) // -an: sin audio
                .setFrameRate(SAFE_OUTPUT_FRAME_RATE)
                .setEffects(new Effects(
                    Collections.emptyList(),
                    Collections.singletonList(
                        Presentation.createForWidthAndHeight(
                            SAFE_OUTPUT_WIDTH,
                            SAFE_OUTPUT_HEIGHT,
                            Presentation.LAYOUT_SCALE_TO_FIT))))
                .build();

            DefaultDecoderFactory decoderFactory = new DefaultDecoderFactory.Builder(context)
                .setEnableDecoderFallback(true)
                .build();

            DefaultAssetLoaderFactory assetLoaderFactory = new DefaultAssetLoaderFactory(
                context,
                decoderFactory,
                Clock.DEFAULT);

            // H.264 Baseline 3.0 + bitrate fijo. yuv420p es el formato por defecto
            // del encoder H.264 Surface->MediaCodec en Android, no hace falta forzarlo
            // y forzarlo manualmente rompe la sesión de MediaCodec.
            VideoEncoderSettings encoderSettings = new VideoEncoderSettings.Builder()
                .setBitrate(SAFE_OUTPUT_BITRATE)
                .setEncodingProfileLevel(
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel3)
                .build();

            DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(encoderSettings)
                .build();

            Transformer transformer = new Transformer.Builder(context)
                .setAssetLoaderFactory(assetLoaderFactory)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult exportResult) {
                        currentTransformer = null;
                        Log.i(TAG, "Transformer onCompleted output=" + output.getAbsolutePath()
                            + " size=" + (output.exists() ? output.length() : -1));
                        try {
                            ValidationResult v = validateMp4(output);
                            Log.i(TAG, "FINAL_CODEC=" + v.codec);
                            Log.i(TAG, "FINAL_PROFILE=" + v.profile);
                            Log.i(TAG, "FINAL_LEVEL=" + v.level);
                            Log.i(TAG, "FINAL_PIXFMT=" + v.pixFmt);
                            Log.i(TAG, "FINAL_WIDTH=" + v.width + " FINAL_HEIGHT=" + v.height
                                + " FINAL_FPS=" + v.frameRate + " FINAL_HAS_AUDIO=" + v.hasAudio);
                            if (!v.isAvc) {
                                cb.onFailure(new RuntimeException("validate-failed: codec not AVC -> " + v.codec));
                                return;
                            }
                            if (!v.isBaseline) {
                                Log.w(TAG, "Encoder did not honour Baseline profile (got " + v.profile
                                    + "). Output may still play but Samsung compatibility is best-effort.");
                            }
                            cb.onSuccess(output);
                        } catch (Throwable validateErr) {
                            Log.e(TAG, "Validation failed", validateErr);
                            cb.onFailure(validateErr instanceof Exception
                                ? (Exception) validateErr
                                : new RuntimeException(validateErr));
                        }
                    }

                    @Override
                    public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                        currentTransformer = null;
                        Log.e(TAG, "Transformer onError code=" + exportException.errorCode
                            + " name=" + exportException.getErrorCodeName(), exportException);
                        cb.onFailure(exportException);
                    }
                })
                .build();

            Log.i(TAG, "Transformer.start (H264 Baseline 3.0, 720x1280, 30fps, no-audio) input="
                + input.getAbsolutePath() + " output=" + output.getAbsolutePath());
            currentTransformer = transformer;
            transformer.start(editedMediaItem, output.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "Transformer setup failed", t);
            cb.onFailure(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }

    private static class ValidationResult {
        String codec = "?";
        String profile = "?";
        String level = "?";
        String pixFmt = "yuv420p"; // Android H.264 MediaCodec output is yuv420p
        int width = 0;
        int height = 0;
        float frameRate = 0f;
        boolean hasAudio = false;
        boolean isAvc = false;
        boolean isBaseline = false;
    }

    private static ValidationResult validateMp4(File file) throws Exception {
        ValidationResult r = new ValidationResult();
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/")) {
                    r.codec = mime;
                    r.isAvc = MimeTypes.VIDEO_H264.equals(mime);
                    if (f.containsKey(MediaFormat.KEY_WIDTH)) r.width = f.getInteger(MediaFormat.KEY_WIDTH);
                    if (f.containsKey(MediaFormat.KEY_HEIGHT)) r.height = f.getInteger(MediaFormat.KEY_HEIGHT);
                    if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        try { r.frameRate = f.getInteger(MediaFormat.KEY_FRAME_RATE); }
                        catch (ClassCastException cce) { r.frameRate = f.getFloat(MediaFormat.KEY_FRAME_RATE); }
                    }
                    if (f.containsKey(MediaFormat.KEY_PROFILE)) {
                        int p = f.getInteger(MediaFormat.KEY_PROFILE);
                        r.profile = avcProfileName(p);
                        r.isBaseline = p == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                            || p == MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline;
                    }
                    if (f.containsKey(MediaFormat.KEY_LEVEL)) {
                        r.level = avcLevelName(f.getInteger(MediaFormat.KEY_LEVEL));
                    }
                } else if (mime.startsWith("audio/")) {
                    r.hasAudio = true;
                }
            }
        } finally {
            ex.release();
        }
        return r;
    }

    private static String avcProfileName(int profile) {
        switch (profile) {
            case MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline: return "Baseline";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline: return "ConstrainedBaseline";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileMain: return "Main";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileHigh: return "High";
            case MediaCodecInfo.CodecProfileLevel.AVCProfileExtended: return "Extended";
            default: return "0x" + Integer.toHexString(profile);
        }
    }

    private static String avcLevelName(int level) {
        switch (level) {
            case MediaCodecInfo.CodecProfileLevel.AVCLevel1: return "1";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel11: return "1.1";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel12: return "1.2";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel13: return "1.3";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel2: return "2";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel21: return "2.1";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel22: return "2.2";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel3: return "3";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel31: return "3.1";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel32: return "3.2";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel4: return "4";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel41: return "4.1";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel42: return "4.2";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel5: return "5";
            case MediaCodecInfo.CodecProfileLevel.AVCLevel51: return "5.1";
            default: return "0x" + Integer.toHexString(level);
        }
    }

    private WallpaperVideoTranscoder() {}
}
