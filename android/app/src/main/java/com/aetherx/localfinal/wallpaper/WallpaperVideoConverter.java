package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.util.Log;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;

/**
 * Transcodes any source video to an Android-safe MP4 (H.264 baseline + yuv420p + AAC + faststart)
 * so Samsung Live Wallpaper / WallpaperService never fails with ERROR_CODE_DECODER_INIT_FAILED.
 */
public final class WallpaperVideoConverter {

    private static final String TAG = "AetherXLiveWP";

    private WallpaperVideoConverter() {}

    public static File convertedDir(Context ctx) {
        File dir = new File(new File(ctx.getFilesDir(), "wallpapers"), "converted");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Synchronous conversion. Returns the output file on success, throws on failure.
     */
    public static File convertToAndroidSafe(Context ctx, File input, String outputName) throws Exception {
        if (input == null || !input.exists() || input.length() <= 0) {
            throw new Exception("convert-input-missing");
        }
        File outDir = convertedDir(ctx);
        String safeName = outputName == null || outputName.isEmpty() ? input.getName() : outputName;
        if (!safeName.toLowerCase().endsWith(".mp4")) safeName = safeName + ".mp4";
        File output = new File(outDir, safeName);
        if (output.exists()) output.delete();

        String cmd = String.format(
            "-y -i \"%s\" -c:v libx264 -pix_fmt yuv420p -profile:v baseline -level 3.0 " +
            "-movflags +faststart -r 30 -c:a aac -b:a 128k \"%s\"",
            input.getAbsolutePath(), output.getAbsolutePath()
        );
        Log.i(TAG, "FFmpeg cmd: " + cmd);

        int rc = FFmpeg.execute(cmd);
        Log.i(TAG, "FFmpeg rc=" + rc);

        if (rc != Config.RETURN_CODE_SUCCESS) {
            String logs = Config.getLastCommandOutput();
            if (logs != null && logs.length() > 2000) logs = logs.substring(logs.length() - 2000);
            Log.e(TAG, "FFmpeg failed logs(tail)=\n" + logs);
            throw new Exception("ffmpeg-failed rc=" + rc);
        }
        if (!output.exists() || output.length() <= 0) {
            throw new Exception("ffmpeg-output-missing");
        }
        Log.i(TAG, "FFmpeg ok output=" + output.getAbsolutePath() + " size=" + output.length());
        return output;
    }
}
