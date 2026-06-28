package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.util.Locale;

/**
 * Transcoder final 100% FFmpegKit + libx264.
 *
 * No usa Media3 Transformer ni MediaCodec encoder. El MP4 resultante se valida
 * con FFprobe y se rechaza si no cumple exactamente:
 * codec_name=h264, profile=Constrained Baseline, pix_fmt=yuv420p, level=30,
 * width=720, height=1280, sin audio, CFR 30 fps.
 */
public final class WallpaperVideoTranscoder {

    private static final String TAG = "AetherXLiveWP";
    private static final int SAFE_OUTPUT_WIDTH = 720;
    private static final int SAFE_OUTPUT_HEIGHT = 1280;

    public interface Callback {
        void onSuccess(File output);
        void onFailure(Exception error);
    }

    public static void transcode(final Context context, final File input, final Callback cb) {
        new Thread(() -> runFFmpeg(context, input, cb), "aetherx-ffmpeg-transcode").start();
    }

    private static void runFFmpeg(final Context context, final File input, final Callback cb) {
        try {
            File outDir = new File(context.getFilesDir(), "wallpapers/converted");
            if (!outDir.exists()) outDir.mkdirs();
            final File output = new File(outDir, "output.mp4");
            if (output.exists() && !output.delete()) {
                Log.w(TAG, "Could not delete previous converted output: " + output.getAbsolutePath());
            }

            String[] command = new String[] {
                "-y",
                "-i", input.getAbsolutePath(),
                "-vf", "scale=720:1280:force_original_aspect_ratio=decrease,pad=720:1280:(ow-iw)/2:(oh-ih)/2,fps=30,format=yuv420p",
                "-c:v", "libx264",
                "-profile:v", "baseline",
                "-level", "3.0",
                "-preset", "ultrafast",
                "-pix_fmt", "yuv420p",
                "-r", "30",
                "-g", "30",
                "-keyint_min", "30",
                "-sc_threshold", "0",
                "-b:v", "2000k",
                "-maxrate", "2000k",
                "-bufsize", "4000k",
                "-movflags", "+faststart",
                "-an",
                output.getAbsolutePath()
            };

            Log.i(TAG, "FFmpegKit.start libx264 baseline level 3.0 720x1280 CFR30 no-audio input="
                + input.getAbsolutePath() + " output=" + output.getAbsolutePath());
            Log.i(TAG, "FFmpeg command: ffmpeg " + joinForLog(command));

            FFmpegSession session = FFmpegKit.executeWithArguments(command);
            ReturnCode rc = session.getReturnCode();
            Log.i(TAG, "FFmpegKit completed state=" + session.getState()
                + " rc=" + rc
                + " outputSize=" + (output.exists() ? output.length() : -1));

            if (!ReturnCode.isSuccess(rc)) {
                String logs = session.getAllLogsAsString();
                String failStack = session.getFailStackTrace();
                Log.e(TAG, "FFmpegKit failed logs=" + logs + " failStack=" + failStack);
                cb.onFailure(new RuntimeException("ffmpeg-failed rc=" + rc + " failStack=" + failStack));
                return;
            }

            if (!output.exists() || output.length() <= 0) {
                cb.onFailure(new RuntimeException("ffmpeg-output-missing"));
                return;
            }

            ValidationResult v = validateMp4WithFFprobe(output);
            Log.i(TAG, "FINAL_CODEC=" + v.codecName);
            Log.i(TAG, "FINAL_PROFILE=" + v.profile);
            Log.i(TAG, "FINAL_LEVEL=" + v.level);
            Log.i(TAG, "FINAL_PIXFMT=" + v.pixFmt);
            Log.i(TAG, "FINAL_WIDTH=" + v.width + " FINAL_HEIGHT=" + v.height
                + " FINAL_AVG_FRAME_RATE=" + v.avgFrameRate + " FINAL_R_FRAME_RATE=" + v.rFrameRate
                + " FINAL_HAS_AUDIO=" + v.hasAudio);

            v.assertStrictlyCompatible();
            cb.onSuccess(output);
        } catch (Throwable t) {
            Log.e(TAG, "FFmpeg transcode failed", t);
            cb.onFailure(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }

    private static class ValidationResult {
        String codecName = "?";
        String profile = "?";
        String pixFmt = "?";
        int level = -1;
        int width = 0;
        int height = 0;
        String rFrameRate = "?";
        String avgFrameRate = "?";
        boolean hasAudio = false;

        void assertStrictlyCompatible() {
            if (!"h264".equals(codecName)) {
                throw new IllegalStateException("ffprobe-validate-failed: codec_name must be h264, got " + codecName);
            }
            if (!"Constrained Baseline".equals(profile)) {
                throw new IllegalStateException("ffprobe-validate-failed: profile must be Constrained Baseline, got " + profile);
            }
            if (!"yuv420p".equals(pixFmt)) {
                throw new IllegalStateException("ffprobe-validate-failed: pix_fmt must be yuv420p, got " + pixFmt);
            }
            if (level != 30) {
                throw new IllegalStateException("ffprobe-validate-failed: level must be 30, got " + level);
            }
            if (width != SAFE_OUTPUT_WIDTH || height != SAFE_OUTPUT_HEIGHT) {
                throw new IllegalStateException("ffprobe-validate-failed: resolution must be 720x1280, got " + width + "x" + height);
            }
            if (hasAudio) {
                throw new IllegalStateException("ffprobe-validate-failed: audio track present; expected -an");
            }
            if (!isThirtyFps(avgFrameRate) && !isThirtyFps(rFrameRate)) {
                throw new IllegalStateException("ffprobe-validate-failed: fps must be 30, avg=" + avgFrameRate + " r=" + rFrameRate);
            }
        }
    }

    private static ValidationResult validateMp4WithFFprobe(File file) {
        ValidationResult r = new ValidationResult();

        String[] videoProbe = new String[] {
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "stream=codec_name,profile,pix_fmt,level,width,height,r_frame_rate,avg_frame_rate",
            "-of", "default=noprint_wrappers=1:nokey=0",
            file.getAbsolutePath()
        };
        FFprobeSession videoSession = FFprobeKit.executeWithArguments(videoProbe);
        if (!ReturnCode.isSuccess(videoSession.getReturnCode())) {
            throw new IllegalStateException("ffprobe-video-failed rc=" + videoSession.getReturnCode()
                + " output=" + videoSession.getOutput()
                + " failStack=" + videoSession.getFailStackTrace());
        }
        parseVideoProbe(videoSession.getOutput(), r);

        String[] audioProbe = new String[] {
            "-v", "error",
            "-select_streams", "a",
            "-show_entries", "stream=codec_type",
            "-of", "csv=p=0",
            file.getAbsolutePath()
        };
        FFprobeSession audioSession = FFprobeKit.executeWithArguments(audioProbe);
        if (!ReturnCode.isSuccess(audioSession.getReturnCode())) {
            throw new IllegalStateException("ffprobe-audio-failed rc=" + audioSession.getReturnCode()
                + " output=" + audioSession.getOutput()
                + " failStack=" + audioSession.getFailStackTrace());
        }
        String audioOut = audioSession.getOutput() == null ? "" : audioSession.getOutput().trim();
        r.hasAudio = !audioOut.isEmpty();
        Log.i(TAG, "FFprobe video output:\n" + videoSession.getOutput());
        Log.i(TAG, "FFprobe audio output:\n" + audioOut);
        return r;
    }

    private static void parseVideoProbe(String output, ValidationResult r) {
        if (output == null) return;
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            switch (key) {
                case "codec_name":
                    r.codecName = value.toLowerCase(Locale.US);
                    break;
                case "profile":
                    r.profile = value;
                    break;
                case "pix_fmt":
                    r.pixFmt = value.toLowerCase(Locale.US);
                    break;
                case "level":
                    r.level = parseInt(value, -1);
                    break;
                case "width":
                    r.width = parseInt(value, 0);
                    break;
                case "height":
                    r.height = parseInt(value, 0);
                    break;
                case "r_frame_rate":
                    r.rFrameRate = value;
                    break;
                case "avg_frame_rate":
                    r.avgFrameRate = value;
                    break;
                default:
                    break;
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private static boolean isThirtyFps(String fps) {
        if (fps == null || fps.trim().isEmpty()) return false;
        String value = fps.trim();
        if ("30".equals(value) || "30.0".equals(value) || "30/1".equals(value) || "30000/1000".equals(value)) {
            return true;
        }
        if (value.contains("/")) {
            String[] parts = value.split("/");
            if (parts.length == 2) {
                try {
                    double n = Double.parseDouble(parts[0]);
                    double d = Double.parseDouble(parts[1]);
                    return d != 0d && Math.abs((n / d) - 30d) < 0.01d;
                } catch (Exception ignored) {}
            }
        }
        try { return Math.abs(Double.parseDouble(value) - 30d) < 0.01d; }
        catch (Exception ignored) { return false; }
    }

    private static String joinForLog(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            if (sb.length() > 0) sb.append(' ');
            if (arg.indexOf(' ') >= 0 || arg.indexOf('(') >= 0 || arg.indexOf(')') >= 0 || arg.indexOf(':') >= 0) {
                sb.append('"').append(arg.replace("\"", "\\\"")).append('"');
            } else {
                sb.append(arg);
            }
        }
        return sb.toString();
    }

    private WallpaperVideoTranscoder() {}
}
