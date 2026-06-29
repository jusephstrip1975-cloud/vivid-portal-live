package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;

/**
 * FFmpegKit ELIMINADO. La APK no embebe libffmpegkit.so y cualquier referencia
 * a com.arthenica.ffmpegkit.* produce NoClassDefFoundError, que mataba el
 * plugin ANTES de poder abrir el picker.
 *
 * El WallpaperService reproduce res/raw/testwallpaper.mp4 directamente, así
 * que no hay transcode que hacer. Esta clase devuelve el archivo de entrada
 * tal cual y persiste SKIP_FFMPEG_RAW_VIDEO para diagnóstico.
 */
public final class WallpaperVideoTranscoder {

    private static final String TAG = "AetherXLiveWP";
    private static final String PREFS = AetherXLiveWallpaperPlugin.PREFS;
    private static final String KEY_LAST_WALLPAPER_STEP =
        AetherXLiveWallpaperPlugin.KEY_LAST_WALLPAPER_STEP;

    public interface Callback {
        void onSuccess(File output);
        void onFailure(Exception error);
    }

    public static void transcode(final Context context, final File input, final Callback cb) {
        try {
            Log.i(TAG, "SKIP_FFMPEG_RAW_VIDEO input=" + (input == null ? "null" : input.getAbsolutePath()));
            if (context != null) {
                SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                p.edit().putString(KEY_LAST_WALLPAPER_STEP,
                    System.currentTimeMillis() + " SKIP_FFMPEG_RAW_VIDEO").apply();
            }
            if (input == null || !input.exists() || input.length() <= 0) {
                cb.onFailure(new RuntimeException("transcode-skip-input-missing"));
                return;
            }
            cb.onSuccess(input);
        } catch (Throwable t) {
            Log.e(TAG, "transcode skip path failed", t);
            cb.onFailure(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }

    private WallpaperVideoTranscoder() {}
}
