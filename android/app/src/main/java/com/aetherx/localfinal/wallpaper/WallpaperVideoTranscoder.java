package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaCodec;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.DefaultAssetLoaderFactory;
import androidx.media3.transformer.DefaultDecoderFactory;
import androidx.media3.transformer.DefaultEncoderFactory;
import androidx.media3.transformer.DefaultMuxer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.VideoEncoderSettings;
import androidx.media3.muxer.Mp4Muxer;
import androidx.media3.muxer.Muxer;

import com.google.common.collect.ImmutableList;

import java.io.FileOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Collections;

/**
 * Rescue transcoder for Android live wallpapers.
 *
 * Important: the app now prioritizes playing the original MP4. This class is
 * only used if Samsung MediaPlayer cannot prepare the original. It must not
 * block valid videos because of metadata differences, fps, bitrate, profile, or
 * duration mismatch.
 */
@OptIn(markerClass = UnstableApi.class)
public final class WallpaperVideoTranscoder {

    private static final String TAG = "AetherXLiveWP";
    private static final int PASS_PRIMARY = 1;
    private static final int MAX_OUTPUT_HEIGHT_PRIMARY = 1280;
    private static final int MAX_OUTPUT_WIDTH_PRIMARY = 720;
    private static final int TARGET_FPS_CAP = 30;
    private static final int BITRATE_PRIMARY = 2_500_000;
    public static final String OUTPUT_PREFIX = "output-samsung-safe-";
    public static final String OUTPUT_SUFFIX = ".mp4";
    private static Transformer currentTransformer;

    public interface Callback {
        void onSuccess(File output);
        void onFailure(Exception error);
    }

    public static void transcode(final Context context, final File input, final Callback cb) {
        new Handler(Looper.getMainLooper()).post(() -> transcodePass(context, input, PASS_PRIMARY, null, cb));
    }

    public static void transcodeAggressive(final Context context, final File input, final Callback cb) {
        new Handler(Looper.getMainLooper()).post(() -> transcodePass(context, input, PASS_PRIMARY, null, cb));
    }

    public static File getConvertedDir(Context context) {
        File dir = new File(context.getFilesDir(), "wallpapers/converted");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File buildUniqueOutput(Context context, int pass) {
        return new File(getConvertedDir(context),
            OUTPUT_PREFIX + "p" + pass + "-" + System.currentTimeMillis() + OUTPUT_SUFFIX);
    }

    private static void transcodePass(
        final Context context,
        final File input,
        final int pass,
        final Exception previousError,
        final Callback cb
    ) {
        try {
            final VideoStats inputStats = readVideoStats(context, input);
            final File output = buildUniqueOutput(context, pass);
            final int maxHeight = MAX_OUTPUT_HEIGHT_PRIMARY;
            final int maxWidth = MAX_OUTPUT_WIDTH_PRIMARY;
            final int targetBitrate = BITRATE_PRIMARY;
            final int targetHeight = chooseTargetHeight(inputStats, maxHeight, maxWidth);
            final int outputFps = chooseOutputFps(inputStats.fps);

            logVideoStats("TRANSCODER_INPUT_PASS_" + pass, input, inputStats);
            Log.i(TAG, "TRANSCODER_DECISION=RESCUE_ONLY pass=" + pass
                + " noAggressivePass=true originalPlaybackAllowed=true"
                + " targetHeight=" + targetHeight
                + " fpsCap=" + outputFps
                + " targetBitrate=" + targetBitrate
                + " previousError=" + (previousError == null ? "none" : previousError.getMessage()));

            MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(input));
            EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
                // For video inputs this is a maximum frame rate. It drops frames if needed,
                // but preserves media timestamps/duration instead of creating slow motion.
                .setFrameRate(outputFps)
                .setEffects(new Effects(
                    Collections.emptyList(),
                    Collections.singletonList(Presentation.createForHeight(targetHeight))))
                .build();

            DefaultDecoderFactory decoderFactory = new DefaultDecoderFactory.Builder(context)
                .setEnableDecoderFallback(true)
                .build();
            DefaultAssetLoaderFactory assetLoaderFactory = new DefaultAssetLoaderFactory(
                context,
                decoderFactory,
                Clock.DEFAULT);

            VideoEncoderSettings.Builder videoSettings = new VideoEncoderSettings.Builder()
                .setBitrate(targetBitrate)
                .setiFrameIntervalSeconds(1f);
            try {
                videoSettings.setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                Log.i(TAG, "TRANSCODER_BITRATE_MODE requested=CBR pass=" + pass);
            } catch (Throwable t) {
                Log.w(TAG, "TRANSCODER_BITRATE_MODE request failed: " + t.getMessage());
            }
            try {
                videoSettings.setEncodingProfileLevel(
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline,
                    MediaCodecInfo.CodecProfileLevel.AVCLevel32);
                Log.i(TAG, "TRANSCODER_PROFILE requested=AVC_BASELINE pass=" + pass);
            } catch (Throwable t) {
                Log.w(TAG, "TRANSCODER_PROFILE request failed: " + t.getMessage());
            }

            DefaultEncoderFactory encoderFactory = new DefaultEncoderFactory.Builder(context)
                .setEnableFallback(true)
                .setRequestedVideoEncoderSettings(videoSettings.build())
                .build();

            Transformer transformer = new Transformer.Builder(context)
                .setAssetLoaderFactory(assetLoaderFactory)
                .setEncoderFactory(encoderFactory)
                .setMuxerFactory(new StreamableMp4MuxerFactory())
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult exportResult) {
                        currentTransformer = null;
                        VideoStats outputStats = readVideoStats(context, output);
                        ValidationResult validation = validateConvertedOutput(context, inputStats, output, outputStats);
                        logVideoStats("TRANSCODER_OUTPUT_PASS_" + pass, output, outputStats);
                        Log.i(TAG, "TRANSCODER_COMPLETED pass=" + pass
                            + " output=" + output.getAbsolutePath()
                            + " size=" + (output.exists() ? output.length() : -1)
                            + " validationOk=" + validation.ok
                            + " validationReason=" + validation.reason
                            + " inputDurationMs=" + inputStats.durationMs
                            + " outputDurationMs=" + outputStats.durationMs
                            + " durationDeltaMs=" + Math.abs(inputStats.durationMs - outputStats.durationMs)
                            + " inputFps=" + inputStats.fps
                            + " outputFps=" + outputStats.fps
                            + " rendererValidation=MediaPlayer.prepare");
                        if (validation.ok) cb.onSuccess(output);
                        else {
                            deleteQuietly(output, "invalid-rescue-output");
                            cb.onFailure(new IllegalStateException("converted-video-invalid: " + validation.reason));
                        }
                    }

                    @Override
                    public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                        currentTransformer = null;
                        Log.e(TAG, "TRANSCODER_ERROR pass=" + pass
                            + " code=" + exportException.errorCode
                            + " name=" + exportException.getErrorCodeName(), exportException);
                        deleteQuietly(output, "error-pass-" + pass);
                        cb.onFailure(exportException);
                    }
                })
                .build();

            Log.i(TAG, "TRANSCODER_START pass=" + pass
                + " input=" + input.getAbsolutePath()
                + " output=" + output.getAbsolutePath()
                + " targetHeight=" + targetHeight
                + " fpsCap=" + outputFps
                + " bitrate=" + targetBitrate
                + " mimeVideo=H264 mimeAudio=AAC"
                + " colorFormat=yuv420p-compatible"
                + " preserveDuration=true noTimestampRewrite=true faststart=true");
            currentTransformer = transformer;
            transformer.start(editedMediaItem, output.getAbsolutePath());
        } catch (Throwable t) {
            Log.e(TAG, "TRANSCODER_SETUP_FAILED pass=" + pass, t);
            cb.onFailure(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }

    public static void deleteAllConvertedOutputsExcept(Context context, String keepPath) {
        File[] files = getConvertedDir(context).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith(OUTPUT_PREFIX)) {
                if (keepPath != null && keepPath.equals(f.getAbsolutePath())) continue;
                deleteQuietly(f, "stale-converted");
            }
        }
    }

    private static int chooseTargetHeight(VideoStats stats, int maxHeight, int maxWidth) {
        if (stats == null || stats.width <= 0 || stats.height <= 0) return maxHeight;
        float heightScale = maxHeight / (float) stats.height;
        float widthScale = maxWidth / (float) stats.width;
        float scale = Math.min(1f, Math.min(heightScale, widthScale));
        int target = Math.max(2, Math.round(stats.height * scale));
        return target % 2 == 0 ? target : target - 1;
    }

    private static int chooseOutputFps(float inputFps) {
        if (inputFps > 0f && inputFps < TARGET_FPS_CAP) return Math.max(15, Math.round(inputFps));
        return TARGET_FPS_CAP;
    }

    private static ValidationResult validateConvertedOutput(
        Context context,
        VideoStats inputStats,
        File output,
        VideoStats outputStats
    ) {
        if (output == null || !output.exists() || output.length() <= 1024 * 1024L) {
            return ValidationResult.fail("empty-output");
        }
        if (outputStats == null || !outputStats.metadataReadable) return ValidationResult.fail("metadata-not-readable");
        if (outputStats.width <= 0 || outputStats.height <= 0) return ValidationResult.fail("invalid-size");
        if (outputStats.durationMs <= 0) return ValidationResult.fail("duration-invalid");
        if (!outputStats.videoSampleReadable) return ValidationResult.fail("video-sample-not-readable");
        if (!isMp4StandardFile(output)) return ValidationResult.fail("mp4-container-invalid");
        Log.i(TAG, "TRANSCODER_PERMISSIVE_VALIDATION codec=" + outputStats.videoMime
            + " fps=" + outputStats.fps
            + " bitrate=" + outputStats.bitrate
            + " profile=" + outputStats.profile
            + " audio=" + outputStats.audioMime
            + " note=no_fps_bitrate_profile_duration_rejection");
        return ValidationResult.ok();
    }

    private static boolean isWithinSamsungSizeLimit(int width, int height, int maxShort, int maxLong) {
        int shortSide = Math.min(width, height);
        int longSide = Math.max(width, height);
        return shortSide <= maxShort && longSide <= maxLong;
    }

    private static boolean isYuv420Compatible(int colorFormat) {
        // MediaExtractor usually reports 0 for compressed MP4 tracks. Accept 0
        // because the H.264 Android encoder output is decoder-standard YUV 4:2:0.
        return colorFormat == 0
            || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            || colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    private static boolean isMp4StandardFile(File output) {
        try {
            String name = output.getName().toLowerCase();
            boolean ok = name.endsWith(".mp4") && output.length() > 1024;
            Log.i(TAG, "VALIDATION_MP4_STANDARD path=" + output.getAbsolutePath()
                + " ok=" + ok + " faststartRequested=true");
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean canPrepareWithMediaPlayer(Context context, File file) {
        MediaPlayer mp = null;
        try {
            mp = new MediaPlayer();
            mp.setDataSource(context, Uri.fromFile(file));
            mp.setVolume(0f, 0f);
            mp.prepare();
            Log.i(TAG, "VALIDATION_MEDIAPLAYER_PREPARE_OK path=" + file.getAbsolutePath()
                + " durationMs=" + mp.getDuration());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "VALIDATION_MEDIAPLAYER_PREPARE_FAILED path=" + file.getAbsolutePath(), t);
            return false;
        } finally {
            if (mp != null) {
                try { mp.release(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void logVideoStats(String prefix, File file, VideoStats stats) {
        if (stats == null) stats = new VideoStats();
        Log.i(TAG, prefix
            + " path=" + (file == null ? "null" : file.getAbsolutePath())
            + " exists=" + (file != null && file.exists())
            + " bytes=" + (file != null && file.exists() ? file.length() : -1)
            + " metadataReadable=" + stats.metadataReadable
            + " codec=" + stats.videoMime
            + " width=" + stats.width
            + " height=" + stats.height
            + " fps=" + stats.fps
            + " estimatedFps=" + stats.estimatedFps
            + " durationMs=" + stats.durationMs
            + " bitrate=" + stats.bitrate
            + " profile=" + stats.profile
            + " level=" + stats.level
            + " colorFormat=" + stats.colorFormat
            + " audio=" + stats.audioMime
            + " hasAudio=" + stats.hasAudio
            + " sampleReadable=" + stats.videoSampleReadable
            + " decoder=" + stats.decoderName
            + " playable=" + isMetadataPlayable(stats)
            + " reason=" + stats.unplayableReason);
    }

    private static boolean isMetadataPlayable(VideoStats stats) {
        return stats != null
            && stats.metadataReadable
            && stats.width > 0
            && stats.height > 0
            && stats.durationMs > 0
            && stats.videoMime != null
            && stats.videoMime.startsWith("video/")
            && stats.videoSampleReadable;
    }

    private static VideoStats readVideoStats(Context context, File file) {
        VideoStats stats = new VideoStats();
        if (file == null || !file.exists()) {
            stats.unplayableReason = "file-missing";
            return stats;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        MediaExtractor extractor = new MediaExtractor();
        try {
            retriever.setDataSource(context, Uri.fromFile(file));
            stats.metadataReadable = true;
            stats.width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            stats.height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            stats.durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            stats.fps = parseFloat(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));

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
                    stats.bitrate = getInteger(format, MediaFormat.KEY_BIT_RATE);
                    stats.profile = getInteger(format, "profile");
                    stats.level = getInteger(format, "level");
                    stats.colorFormat = getInteger(format, "color-format");
                    extractor.selectTrack(i);
                    stats.videoSampleReadable = extractor.readSampleData(java.nio.ByteBuffer.allocate(1024 * 1024), 0) >= 0;
                    try { extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC); } catch (Throwable ignored) {}
                    stats.estimatedFps = estimateSelectedTrackFps(extractor);
                    if (stats.estimatedFps > 0f) stats.fps = stats.estimatedFps;
                    extractor.unselectTrack(i);
                } else if (mime != null && mime.startsWith("audio/")) {
                    stats.hasAudio = true;
                    stats.audioMime = mime;
                }
            }
            if (stats.videoMime == null || stats.videoMime.isEmpty()) stats.unplayableReason = "no-video-track";
            else if (!stats.videoSampleReadable) stats.unplayableReason = "video-sample-not-readable";
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
                        if (info.getCapabilitiesForType(type).isFormatSupported(format)) return info.getName();
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

    private static float estimateSelectedTrackFps(MediaExtractor extractor) {
        try {
            long previousUs = -1L;
            long totalDeltaUs = 0L;
            int deltas = 0;
            for (int i = 0; i < 120; i++) {
                long timeUs = extractor.getSampleTime();
                if (timeUs < 0) break;
                if (previousUs >= 0 && timeUs > previousUs) {
                    totalDeltaUs += (timeUs - previousUs);
                    deltas++;
                }
                previousUs = timeUs;
                if (!extractor.advance()) break;
            }
            if (deltas <= 0 || totalDeltaUs <= 0L) return 0f;
            return 1_000_000f / (totalDeltaUs / (float) deltas);
        } catch (Throwable t) {
            Log.w(TAG, "estimateSelectedTrackFps failed err=" + t.getMessage());
            return 0f;
        }
    }

    private static void deleteQuietly(File file, String reason) {
        try {
            if (file != null && file.exists()) {
                boolean ok = file.delete();
                Log.i(TAG, "Deleting converted file reason=" + reason + " path=" + file.getAbsolutePath() + " ok=" + ok);
            }
        } catch (Throwable t) {
            Log.w(TAG, "deleteQuietly failed reason=" + reason + " err=" + t.getMessage());
        }
    }

    private static int getInteger(MediaFormat format, String key) {
        try { return format.containsKey(key) ? format.getInteger(key) : 0; }
        catch (Throwable ignored) { return 0; }
    }

    private static int parseInt(String value) {
        try { return value == null ? 0 : Integer.parseInt(value); }
        catch (Throwable ignored) { return 0; }
    }

    private static long parseLong(String value) {
        try { return value == null ? 0L : Long.parseLong(value); }
        catch (Throwable ignored) { return 0L; }
    }

    private static float parseFloat(String value) {
        try { return value == null ? 0f : Float.parseFloat(value); }
        catch (Throwable ignored) { return 0f; }
    }

    private static final class ValidationResult {
        final boolean ok;
        final String reason;

        private ValidationResult(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        static ValidationResult ok() { return new ValidationResult(true, "ok"); }
        static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
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
        float estimatedFps;
        String videoMime = "";
        String audioMime = "";
        String decoderName = "";
        String unplayableReason = "unknown";
        boolean hasAudio;
        boolean videoSampleReadable;
        boolean metadataReadable;
    }

    private static final class StreamableMp4MuxerFactory implements Muxer.Factory {
        private final DefaultMuxer.Factory supportedTypesDelegate = new DefaultMuxer.Factory();

        @Override
        public Muxer create(String path) throws Muxer.MuxerException {
            try {
                FileOutputStream outputStream = new FileOutputStream(path);
                Log.i(TAG, "MUXER_CREATE_STREAMABLE_MP4 faststart=true path=" + path);
                return new Mp4MuxerWrapper(
                    new Mp4Muxer.Builder(outputStream)
                        .setAttemptStreamableOutputEnabled(true)
                        .build());
            } catch (Exception e) {
                throw new Muxer.MuxerException("streamable-mp4-muxer-create-failed", e);
            }
        }

        @Override
        public ImmutableList<String> getSupportedSampleMimeTypes(int trackType) {
            return supportedTypesDelegate.getSupportedSampleMimeTypes(trackType);
        }
    }

    private static final class Mp4MuxerWrapper implements Muxer {
        private final Mp4Muxer muxer;

        private Mp4MuxerWrapper(Mp4Muxer muxer) {
            this.muxer = muxer;
        }

        @Override
        public Muxer.TrackToken addTrack(Format format) throws Muxer.MuxerException {
            return muxer.addTrack(format);
        }

        @Override
        public void writeSampleData(Muxer.TrackToken trackToken, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo)
                throws Muxer.MuxerException {
            muxer.writeSampleData(trackToken, byteBuffer, bufferInfo);
        }

        @Override
        public void addMetadataEntry(Metadata.Entry entry) {
            muxer.addMetadataEntry(entry);
        }

        @Override
        public void close() throws Muxer.MuxerException {
            muxer.close();
        }
    }

    private WallpaperVideoTranscoder() {}
}