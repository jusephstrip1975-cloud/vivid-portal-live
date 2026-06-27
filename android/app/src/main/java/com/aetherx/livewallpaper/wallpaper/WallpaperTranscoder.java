package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * Normalises any downloaded MP4 to Samsung Live Wallpaper safe format:
 * H.264 baseline (AVC) / 1080x1920 PORTRAIT / max 30fps / yuv420p / no audio / faststart.
 *
 * The previous Android transformer is intentionally not used here. Samsung OneUI
 * kept detecting a physically landscape stream (1920x1080) despite portrait
 * metadata. FFmpegKit + libx264 is the mandatory transcoder for Samsung wallpapers.
 */
public final class WallpaperTranscoder {

    private static final String TAG = "AetherXLiveWP";

    public static File transcodeToSamsungSafe(Context ctx, File input, File finalOutput) throws Exception {
        if (input == null || !input.exists()) throw new Exception("transcode-input-missing");

        WallpaperProbe srcProbe = WallpaperProbe.of(input);
        Log.i(TAG, "TRANSCODE_PLAN engine=FFmpegKit srcSize=" + srcProbe.width + "x" + srcProbe.height
            + " target=1080x1920@30 H264-baseline yuv420p noAudio SAR=1 faststart SDR");

        File tmpOutput = new File(finalOutput.getParentFile(), "current_transcoded.mp4");
        if (tmpOutput.exists() && !tmpOutput.delete()) {
            Log.w(TAG, "TRANSCODE_TMP_DELETE_FAILED path=" + tmpOutput.getAbsolutePath());
        }

        String[] ffmpegArgs = new String[] {
            "-y",
            "-i", input.getAbsolutePath(),
            "-vf", "transpose=1,scale=1080:1920,setsar=1",
            "-r", "30",
            "-an",
            "-c:v", "libx264",
            "-profile:v", "baseline",
            "-level", "3.1",
            "-pix_fmt", "yuv420p",
            "-colorspace", "bt709",
            "-color_primaries", "bt709",
            "-color_trc", "bt709",
            "-map_metadata", "-1",
            "-metadata:s:v:0", "rotate=0",
            "-movflags", "+faststart",
            tmpOutput.getAbsolutePath()
        };
        String ffmpegCommand = joinArgsForLog(ffmpegArgs);
        Log.i(TAG, "FFMPEG_COMMAND=" + ffmpegCommand);
        persistString(ctx, "ffmpeg_command", ffmpegCommand);
        persistString(ctx, "ffmpeg_exit_code", null);

        FFmpegSession session = FFmpegKit.executeWithArguments(ffmpegArgs);
        String exitCode = String.valueOf(session.getReturnCode());
        Log.i(TAG, "FFMPEG_EXIT_CODE=" + exitCode
            + " state=" + session.getState()
            + " failStack=" + session.getFailStackTrace());
        persistString(ctx, "ffmpeg_exit_code", exitCode);

        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            deleteQuietly(tmpOutput, "TRANSCODE_FFMPEG_FAILED_DELETE");
            String output = session.getOutput();
            if (output != null && output.length() > 1200) output = output.substring(output.length() - 1200);
            Log.e(TAG, "TRANSCODE_FFMPEG_FAILED output=" + output);
            throw new Exception("FAIL_FFMPEG_TRANSCODE:exit=" + exitCode);
        }
        if (!tmpOutput.exists() || tmpOutput.length() < 1024L * 1024L) {
            throw new Exception("transcode-output-invalid:size=" + (tmpOutput.exists() ? tmpOutput.length() : -1));
        }

        FFprobeResult ffprobe = probeWithFFprobe(ctx, tmpOutput);
        validateFFprobeResultOrFail(tmpOutput, ffprobe);

        WallpaperProbe outProbe = WallpaperProbe.of(tmpOutput);
        Log.i(TAG, "OUTPUT_PROBE codec=" + outProbe.codec
            + " OUTPUT_WIDTH=" + outProbe.width
            + " OUTPUT_HEIGHT=" + outProbe.height
            + " OUTPUT_FPS=" + outProbe.fps
            + " OUTPUT_HAS_AUDIO=" + outProbe.hasAudio
            + " OUTPUT_ROTATION=" + ffprobe.rotation
            + " OUTPUT_COLOR_STANDARD=bt709 OUTPUT_COLOR_TRANSFER=bt709");
        if (outProbe.width <= 0 || outProbe.height <= 0) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_PROBE_DELETE");
            throw new Exception("FAIL_TRANSCODE_INVALID_PROBE");
        }
        if (outProbe.width > outProbe.height) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_ORIENTATION_DELETE");
            throw new Exception("FAIL_TRANSCODE_INVALID_ORIENTATION:"
                + outProbe.width + "x" + outProbe.height);
        }
        if (outProbe.width != WallpaperProbe.TARGET_WIDTH
            || outProbe.height != WallpaperProbe.TARGET_HEIGHT) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_OUTPUT_SIZE_DELETE");
            throw new Exception("FAIL_TRANSCODE_INVALID_SIZE:"
                + outProbe.width + "x" + outProbe.height);
        }
        if (outProbe.hasAudio) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_AUDIO_DELETE");
            throw new Exception("FAIL_TRANSCODE_AUDIO_PRESENT");
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

    private static FFprobeResult probeWithFFprobe(Context ctx, File file) throws Exception {
        String[] ffprobeArgs = new String[] {
            "-v", "error",
            "-show_entries", "stream=codec_type,codec_name,profile,width,height,pix_fmt,r_frame_rate,avg_frame_rate,sample_aspect_ratio,color_space,color_transfer,color_primaries:stream_tags=rotate:stream_side_data=rotation",
            "-of", "json",
            file.getAbsolutePath()
        };
        FFprobeSession session = FFprobeKit.executeWithArguments(ffprobeArgs);
        String exitCode = String.valueOf(session.getReturnCode());
        String output = session.getOutput();
        Log.i(TAG, "FFPROBE_EXIT_CODE=" + exitCode + " output=" + output);
        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            throw new Exception("FAIL_FFPROBE:exit=" + exitCode);
        }

        FFprobeResult result = new FFprobeResult();
        try {
            JSONObject root = new JSONObject(output == null ? "{}" : output);
            JSONArray streams = root.optJSONArray("streams");
            if (streams != null) {
                for (int s = 0; s < streams.length(); s++) {
                    JSONObject stream = streams.optJSONObject(s);
                    if (stream == null) continue;
                    String codecType = stream.optString("codec_type", "");
                    if ("audio".equals(codecType)) {
                        result.hasAudio = true;
                        continue;
                    }
                    if (!"video".equals(codecType) || result.width > 0) continue;
                    result.codec = stream.optString("codec_name", "");
                    result.profile = stream.optString("profile", "");
                    result.width = stream.optInt("width", 0);
                    result.height = stream.optInt("height", 0);
                    result.pixFmt = stream.optString("pix_fmt", "");
                    result.fps = parseFps(stream.optString("avg_frame_rate", "0/0"));
                    if (result.fps <= 0) result.fps = parseFps(stream.optString("r_frame_rate", "0/0"));
                    result.sampleAspectRatio = stream.optString("sample_aspect_ratio", "");
                    result.colorSpace = stream.optString("color_space", "");
                    result.colorTransfer = stream.optString("color_transfer", "");
                    result.colorPrimaries = stream.optString("color_primaries", "");
                    JSONObject tags = stream.optJSONObject("tags");
                    if (tags != null) result.rotation = parseRotation(tags.optString("rotate", "0"));
                    JSONArray sideData = stream.optJSONArray("side_data_list");
                    if (sideData != null) {
                        for (int i = 0; i < sideData.length(); i++) {
                            JSONObject item = sideData.optJSONObject(i);
                            if (item != null && item.has("rotation")) {
                                result.rotation = parseRotation(item.optString("rotation", "0"));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "FFPROBE_PARSE_FAILED", t);
            throw new Exception("FAIL_FFPROBE_PARSE:" + t.getMessage());
        }

        Log.i(TAG, "FFPROBE_WIDTH=" + result.width);
        Log.i(TAG, "FFPROBE_HEIGHT=" + result.height);
        Log.i(TAG, "FFPROBE_ROTATION=" + result.rotation);
        Log.i(TAG, "FFPROBE_CODEC=" + result.codec
            + " profile=" + result.profile
            + " pix_fmt=" + result.pixFmt
            + " fps=" + result.fps
            + " sar=" + result.sampleAspectRatio
            + " hasAudio=" + result.hasAudio
            + " colorSpace=" + result.colorSpace
            + " colorTransfer=" + result.colorTransfer
            + " colorPrimaries=" + result.colorPrimaries);
        SharedPreferences.Editor e = ctx.getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE).edit();
        e.putInt("ffprobe_width", result.width);
        e.putInt("ffprobe_height", result.height);
        e.putInt("ffprobe_rotation", result.rotation);
        e.commit();
        return result;
    }

    private static void validateFFprobeResultOrFail(File tmpOutput, FFprobeResult p) throws Exception {
        // RELAXED validator (2.1.1-validator-fix). Only the strict required checks remain:
        // width=1080, height=1920, rotation=0, pix_fmt=yuv420p, hasAudio=false.
        // Profile / codec name / fps / SAR / color metadata are NOT rejected — FFmpeg already
        // produced the file with the right -profile baseline flag; ffprobe may report the
        // profile as a numeric profile_idc (e.g. 66, 578) which is valid Baseline/Constrained Baseline.
        if (p.width != WallpaperProbe.TARGET_WIDTH || p.height != WallpaperProbe.TARGET_HEIGHT) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_SIZE_DELETE");
            throw new Exception("FAIL_TRANSCODE_INVALID_SIZE:" + p.width + "x" + p.height);
        }
        if (p.rotation != 0) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_ROTATION_DELETE");
            throw new Exception("FAIL_TRANSCODE_ROTATION_METADATA:" + p.rotation);
        }
        if (!"yuv420p".equalsIgnoreCase(p.pixFmt)) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_PIXFMT_DELETE");
            throw new Exception("FAIL_TRANSCODE_INVALID_PIXFMT:" + p.pixFmt);
        }
        if (p.hasAudio) {
            deleteQuietly(tmpOutput, "TRANSCODE_INVALID_AUDIO_STREAM_DELETE");
            throw new Exception("FAIL_TRANSCODE_AUDIO_PRESENT");
        }
        Log.i(TAG, "VALIDATOR_OK_RELAXED profile=" + p.profile + " codec=" + p.codec
            + " fps=" + p.fps + " sar=" + p.sampleAspectRatio
            + " colorSpace=" + p.colorSpace + " colorTransfer=" + p.colorTransfer);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static String joinArgsForLog(String[] args) {
        StringBuilder sb = new StringBuilder("ffmpeg");
        for (String arg : args) {
            sb.append(' ');
            if (arg == null) {
                sb.append("null");
            } else if (arg.indexOf(' ') >= 0 || arg.indexOf('"') >= 0) {
                sb.append('"').append(arg.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else {
                sb.append(arg);
            }
        }
        return sb.toString();
    }

    private static void persistString(Context ctx, String key, String value) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE).edit();
        if (value == null) e.remove(key); else e.putString(key, value);
        e.commit();
    }

    private static void deleteQuietly(File file, String label) {
        if (file == null) return;
        boolean existed = file.exists();
        boolean deleted = !existed || file.delete();
        Log.i(TAG, label + " path=" + file.getAbsolutePath() + " existed=" + existed + " deleted=" + deleted);
    }

    private static final class FFprobeResult {
        String codec = "";
        String profile = "";
        String pixFmt = "";
        String sampleAspectRatio = "";
        String colorSpace = "";
        String colorTransfer = "";
        String colorPrimaries = "";
        int width = 0;
        int height = 0;
        int rotation = 0;
        double fps = 0;
        boolean hasAudio = false;
    }
    private static float parseFps(String fps) {
        try {
            if (fps == null || fps.isEmpty() || fps.equals("0/0")) return 0f;
            if (fps.contains("/")) {
                String[] parts = fps.split("/");
                float num = Float.parseFloat(parts[0]);
                float den = Float.parseFloat(parts[1]);
                if (den == 0f) return 0f;
                return num / den;
            }
            return Float.parseFloat(fps);
        } catch (Exception e) {
            return 0f;
        }
    }

    private static int parseRotation(String rotation) {
        try {
            if (rotation == null || rotation.isEmpty()) return 0;
            return Integer.parseInt(rotation.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
