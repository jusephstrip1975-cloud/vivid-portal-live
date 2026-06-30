package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.aetherx.livewallpaper.R;

/**
 * Samsung One UI hardened WallpaperService.
 * Records every lifecycle step into SharedPreferences for diagnostics.
 */
public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";

    private void recordStep(String step) {
        Log.i(TAG, "STEP " + step);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            p.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_WALLPAPER_STEP,
                System.currentTimeMillis() + " " + step).apply();
        } catch (Throwable ignored) {}
    }

    private void recordKey(String key, String value) {
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            p.edit().putString(key, System.currentTimeMillis() + " " + value).apply();
        } catch (Throwable ignored) {}
    }

    private void recordError(String key, Throwable t) {
        if (t == null) return;
        Log.e(TAG, key, t);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage() + "\n" + Log.getStackTraceString(t);
            p.edit().putString(key, System.currentTimeMillis() + " " + msg).apply();
        } catch (Throwable ignored) {}
    }

    private void persistStep(String step) {
        recordStep(step);
    }

    private void persistNativeException(String message) {
        Log.e(TAG, message);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            p.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_NATIVE_EXCEPTION,
                System.currentTimeMillis() + " " + message).apply();
        } catch (Throwable ignored) {}
    }

    private void persistNativeException(String message, Throwable t) {
        Log.e(TAG, message, t);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String stack = t == null ? "" : "\n" + Log.getStackTraceString(t);
            p.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_NATIVE_EXCEPTION,
                System.currentTimeMillis() + " " + message + stack).apply();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SERVICE_ONCREATE");
        recordStep("SERVICE_ONCREATE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreate");
    }

    @Override
    public Engine onCreateEngine() {
        Log.i(TAG, "ON_CREATE_ENGINE");
        recordStep("ON_CREATE_ENGINE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreateEngine");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "onCreateEngine");
        return new RawVideoEngine();
    }

    private class RawVideoEngine extends Engine {
        private MediaPlayer player;
        private SurfaceHolder currentHolder;
        private boolean prepared = false;
        private boolean visible = false;
        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            try {
                surfaceHolder.setFixedSize(720, 1280);
            } catch (Throwable t) {
                Log.w(TAG, "setFixedSize failed", t);
            }
            Log.i(TAG, "ENGINE_CREATED");
            recordStep("ENGINE_CREATED");
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "engineOnCreate");
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Surface s = holder.getSurface();
            boolean valid = s != null && s.isValid();
            Log.i(TAG, "ON_SURFACE_CREATED valid=" + valid);
            recordStep("ON_SURFACE_CREATED valid=" + valid);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceCreated valid=" + valid);
            paintMessage("Cargando wallpaper...");
            if (valid) main.post(this::startRawPlayer);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            Log.i(TAG, "SURFACE_CHANGED " + width + "x" + height);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceChanged " + width + "x" + height);
            main.post(() -> {
                if (player == null) {
                    startRawPlayer();
                } else {
                    try {
                        player.setSurface(holder.getSurface());
                    } catch (Throwable t) {
                        Log.e(TAG, "setSurface failed", t);
                    }
                }
            });
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            visible = v;
            Log.i(TAG, "VISIBILITY=" + v);
            main.post(() -> {
                if (player == null || !prepared) return;
                try {
                    if (!v && player.isPlaying()) player.pause();
                } catch (Throwable t) {
                    Log.e(TAG, "visibility toggle failed", t);
                }
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "SURFACE_DESTROYED");
            main.post(this::releasePlayer);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "ENGINE_DESTROYED");
            main.post(this::releasePlayer);
            super.onDestroy();
        }

        private void startRawPlayer() {
            try {
                releasePlayer();

                SurfaceHolder holder = currentHolder;
                if (holder == null) {
                    persistStep("MEDIA_HOLDER_NULL");
                    return;
                }

                Surface surface = holder.getSurface();
                if (surface == null) {
                    persistStep("MEDIA_SURFACE_NULL");
                    return;
                }

                if (surface == null || !surface.isValid()) {
                    persistStep("MEDIA_SURFACE_INVALID");
                    return;
                }
                persistStep("MEDIA_SURFACE_VALID");

                player = new MediaPlayer();
                persistStep("MEDIA_PLAYER_CREATED");

                player.reset();
                persistStep("MEDIA_RESET");

                player.setSurface(surface);
                persistStep("MEDIA_SURFACE_ATTACHED");

                player.setLooping(true);
                persistStep("MEDIA_LOOPING_SET");

                player.setVolume(0f, 0f);
                persistStep("MEDIA_VOLUME_MUTED");

                player.setScreenOnWhilePlaying(true);
                persistStep("MEDIA_SCREEN_ON_SET");

                player.setDataSource(
                    AetherXLiveWallpaperService.this,
                    Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.testwallpaper)
                );
                persistStep("MEDIA_DATASOURCE_SET");

                player.setOnPreparedListener(mp -> {
                    prepared = true;
                    persistStep("MEDIA_PREPARED");
                    try {
                        mp.start();
                        persistStep("MEDIA_STARTED");
                    } catch (Throwable t) {
                        persistNativeException("MEDIA_START_EXCEPTION", t);
                    }
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    String msg = "MEDIA_ERROR what=" + what + " extra=" + extra;
                    persistStep("MEDIA_ERROR");
                    persistNativeException(msg);
                    try {
                        SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                        p.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_ERROR,
                            System.currentTimeMillis() + " " + msg).apply();
                    } catch (Throwable ignored) {}
                    paintMessage("Error " + what + "/" + extra);
                    return true;
                });

                player.prepareAsync();
                persistStep("MEDIA_PREPARE_ASYNC");
            } catch (Throwable t) {
                persistNativeException("MEDIA_START_RAW_PLAYER_EXCEPTION", t);
                paintMessage("Fallo: " + t.getClass().getSimpleName());
            }
        }

        private void paintMessage(String text) {
            try {
                if (currentHolder == null) return;
                Surface s = currentHolder.getSurface();
                if (s == null || !s.isValid()) return;
                Canvas c = currentHolder.lockCanvas();
                if (c == null) return;
                c.drawColor(Color.BLACK);
                Paint p = new Paint();
                p.setColor(Color.WHITE);
                p.setAntiAlias(true);
                p.setTextSize(36f);
                c.drawText(text == null ? "" : text, 40f, c.getHeight() / 2f, p);
                currentHolder.unlockCanvasAndPost(c);
            } catch (Throwable ignored) {}
        }

        private void releasePlayer() {
            prepared = false;
            if (player != null) {
                try { player.setOnPreparedListener(null); } catch (Throwable ignored) {}
                try { player.setOnErrorListener(null); } catch (Throwable ignored) {}
                try { player.stop(); } catch (Throwable ignored) {}
                try { player.release(); } catch (Throwable ignored) {}
                player = null;
            }
        }
    }
}
