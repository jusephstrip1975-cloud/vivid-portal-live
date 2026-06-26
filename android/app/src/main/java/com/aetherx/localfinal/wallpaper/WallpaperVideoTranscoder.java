package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
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
    private static final int SAFE_OUTPUT_BITRATE_30 = 3_000_000;
    private static final int SAFE_OUTPUT_BITRATE_60 = 4_500_000;
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
            final File output = buildUniqueOutput(context);
            final VideoStats inputStats = readVideoStats(context, input);
            logVideoStats("TRANSCODER_INPUT", input, inputStats);
            if (isSamsungSafePassthrough(inputStats)) {
                Log.i(TAG, "TRANSCODER_DECISION=PASSTHROUGH_ORIGINAL_ALREADY_SAMSUNG_SAFE path="
                    + input.getAbsolutePath());
                cb.onSuccess(input);
                return;
            }
            Log.i(TAG, "Transcode unique output target=" + output.getAbsolutePath());
            Log.i(TAG, "Transcoder inputDurationMs=" + inputStats.durationMs
                + " inputFps=" + inputStats.fps
                + " inputSize=" + inputStats.width + "x" + inputStats.height
                + " inputCodec=" + inputStats.videoMime
                + " inputBitrate=" + inputStats.bitrate
                + " inputProfile=" + inputStats.profile
                + " inputLevel=" + inputStats.level
                + " inputColorFormat=" + inputStats.colorFormat
                + " inputAudio=" + inputStats.audioMime
                + " inputDecoder=" + inputStats.decoderName
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

            int targetBitrate = inputStats.fps >= 45f ? SAFE_OUTPUT_BITRATE_60 : SAFE_OUTPUT_BITRATE_30;
            VideoEncoderSettings.Builder videoSettings = new VideoEncoderSettings.Builder()
                .setBitrate(targetBitrate)
                .setiFrameIntervalSeconds(1f);

            DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(context)
                .setEnableFallback(true)
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
                        logVideoStats("TRANSCODER_OUTPUT", output, outputStats);
                        Log.i(TAG, "Transformer onCompleted output=" + output.getAbsolutePath()
                            + " size=" + (output.exists() ? output.length() : -1));
                        Log.i(TAG, "Transcoder outputDurationMs=" + outputStats.durationMs
                            + " outputFps=" + outputStats.fps
                            + " outputSize=" + outputStats.width + "x" + outputStats.height
                            + " outputCodec=" + outputStats.videoMime
                            + " outputBitrate=" + outputStats.bitrate
                            + " outputProfile=" + outputStats.profile
                            + " outputLevel=" + outputStats.level
                            + " outputColorFormat=" + outputStats.colorFormat
                            + " outputAudio=" + outputStats.audioMime
                            + " outputDecoder=" + outputStats.decoderName
                            + " inputDurationMs=" + inputStats.durationMs
                            + " inputFps=" + inputStats.fps
                            + " durationDeltaMs=" + durationDeltaMs);
                        currentTransformer = null;
                        if (output == null || !output.exists() || output.length() <= 0) {
                            Log.e(TAG, "TRANSCODER_OUTPUT_EMPTY rejecting");
                            cb.onFailure(new IllegalStateException("converted-video-empty"));
                            return;
                        }
                        if (!isPlayableVideo(outputStats)) {
                            Log.e(TAG, "TRANSCODER_OUTPUT_NOT_PLAYABLE fallbackToOriginal=true reason="
                                + outputStats.unplayableReason);
                            cb.onFailure(new IllegalStateException("converted-video-not-playable: "
                                + outputStats.unplayableReason));
                            return;
                        }
                        if (inputStats.durationMs > 0 && outputStats.durationMs > 0) {
                            long allowedDelta = Math.max(750L, inputStats.durationMs / 20L); // 5% tolerance.
                            Log.i(TAG, "TRANSCODER_DURATION_INFO inputDurationMs=" + inputStats.durationMs
                                + " outputDurationMs=" + outputStats.durationMs
                                + " durationDeltaMs=" + durationDeltaMs
                                + " allowedDeltaMs=" + allowedDelta);
                            if (durationDeltaMs > allowedDelta) {
                                Log.e(TAG, "TRANSCODER_DURATION_MISMATCH fallbackToOriginal=true avoidsSlowMotion=true");
                                cb.onFailure(new IllegalStateException("converted-duration-mismatch"));
                                return;
                            }
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
                + " output=" + output.getAbsolutePath()
                + " targetHeight=" + SAFE_OUTPUT_HEIGHT
                + " targetBitrate=" + targetBitrate
                + " fpsMode=preserve-source-timestamps");
            currentTransformer = transformer;
            transformer.start(editedMediaItem, output.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "Transformer setup failed", t);
            cb.onFailure(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }

    public static void deleteAllConvertedOutputsExcept(Context context, String keepPath) {
        deleteAllConvertedOutputsExcept(getConvertedDir(context), keepPath);
    }

    private static void deleteAllConvertedOutputsExcept(File outDir, String keepPath) {
        File[] files = outDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith(OUTPUT_PREFIX)) {
                if (keepPath != null && keepPath.equals(f.getAbsolutePath())) continue;
                boolean ok = f.delete();
                Log.i(TAG, "Deleting stale converted file=" + f.getAbsolutePath() + " ok=" + ok);
            }
        }
    }

    private static boolean isPlayableVideo(VideoStats stats) {
        return stats != null
            && stats.width > 0
            && stats.height > 0
            && stats.durationMs > 0
            && stats.videoMime != null
            && stats.videoMime.startsWith("video/")
            && stats.videoSampleReadable
            && stats.decoderName != null
            && !stats.decoderName.isEmpty();
    }

    private static boolean isSamsungSafePassthrough(VideoStats stats) {
        if (!isPlayableVideo(stats)) return false;
        if (!MimeTypes.VIDEO_H264.equals(stats.videoMime)) return false;
        if (stats.height > 1080 || stats.width > 1920) return false;
        if (stats.fps > 61f) return false;
        return true;
    }

    private static void logVideoStats(String prefix, File file, VideoStats stats) {
        Log.i(TAG, prefix
            + " path=" + (file == null ? "null" : file.getAbsolutePath())
            + " exists=" + (file != null && file.exists())
            + " bytes=" + (file != null && file.exists() ? file.length() : -1)
            + " codec=" + stats.videoMime
            + " width=" + stats.width
            + " height=" + stats.height
            + " fps=" + stats.fps
            + " durationMs=" + stats.durationMs
            + " bitrate=" + stats.bitrate
            + " profile=" + stats.profile
            + " level=" + stats.level
            + " colorFormat=" + stats.colorFormat
            + " audio=" + stats.audioMime
            + " hasAudio=" + stats.hasAudio
            + " sampleReadable=" + stats.videoSampleReadable
            + " decoder=" + stats.decoderName
            + " playable=" + isPlayableVideo(stats)
            + " reason=" + stats.unplayableReason);
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
                    stats.videoMime = mime;
                    stats.decoderName = findDecoderName(format, mime);
                    if (stats.fps <= 0f && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        stats.fps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                    }
                    if (stats.durationMs <= 0 && format.containsKey(MediaFormat.KEY_DURATION)) {
                        stats.durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L;
                    }
                    stats.bitrate = getInteger(format, "bitrate");
                    stats.profile = getInteger(format, "profile");
                    stats.level = getInteger(format, "level");
                    stats.colorFormat = getInteger(format, "color-format");
                    extractor.selectTrack(i);
                    stats.videoSampleReadable = extractor.readSampleData(java.nio.ByteBuffer.allocate(1), 0) >= 0;
                    extractor.unselectTrack(i);
                } else if (mime != null && mime.startsWith("audio/")) {
                    stats.hasAudio = true;
                    stats.audioMime = mime;
                    if (stats.audioMime == null || stats.audioMime.isEmpty()) stats.audioMime = mime;
                }
            }
            if (stats.videoMime == null || stats.videoMime.isEmpty()) stats.unplayableReason = "no-video-track";
            else if (!stats.videoSampleReadable) stats.unplayableReason = "video-sample-not-readable";
            else if (stats.decoderName == null || stats.decoderName.isEmpty()) stats.unplayableReason = "no-decoder-for-format";
            else if (stats.durationMs <= 0) stats.unplayableReason = "duration-missing";
            else stats.unplayableReason = "ok";
        } catch (Throwable t) {
            Log.e(TAG, "Video metadata read failed file=" + file.getAbsolutePath(), t);
            stats.unplayableReason = t.getClass().getSimpleName() + ":" + t.getMessage();
        } finally {
            try { retriever.release(); } catch (Throwable ignored) {}
            try { extractor.release(); } catch (Throwable ignored) {}
        }
        return stats;
    }

    private static String findDecoderName(MediaFormat format, String mime) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            String direct = list.findDecoderForFormat(format);
            if (direct != null && !direct.isEmpty()) return direct;
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (info.isEncoder()) continue;
                String[] types = info.getSupportedTypes();
                if (types == null) continue;
                for (String type : types) {
                    if (!mime.equalsIgnoreCase(type)) continue;
                    try {
                        if (info.getCapabilitiesForType(type).isFormatSupported(format)) {
                            return info.getName();
                        }
                    } catch (Throwable ignored) {
                        return info.getName();
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "findDecoderName failed mime=" + mime + " err=" + t.getMessage());
        }
        return "";
    }

    private static int getInteger(MediaFormat format, String key) {
        try {
            return format.containsKey(key) ? format.getInteger(key) : 0;
        } catch (Throwable ignored) {
            return 0;
        }
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
        int bitrate;
        int profile;
        int level;
        int colorFormat;
        String videoMime = "";
        String audioMime = "";
        String decoderName = "";
        String unplayableReason = "unknown";
        boolean hasAudio;
        boolean videoSampleReadable;
    }

    private WallpaperVideoTranscoder() {}
}
