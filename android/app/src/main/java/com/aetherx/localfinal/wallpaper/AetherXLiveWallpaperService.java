package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.aetherx.localfinal.R;

/**
 * Samsung-hardened lifecycle build with diagnostics recording.
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

    private void recordError(String key, Throwable t) {
        if (t == null) return;
        Log.e(TAG, key, t);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
            p.edit().putString(key, System.currentTimeMillis() + " " + msg).apply();
        } catch (Throwable ignored) {}
    }


    @Override
    public Engine onCreateEngine() {
        Log.i(TAG, "ENGINE_CREATED");
        recordStep("ENGINE_CREATED");
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
                // Samsung wallpaper engine prefers a fixed surface size.
                surfaceHolder.setFixedSize(720, 1280);
            } catch (Throwable t) {
                Log.w(TAG, "setFixedSize failed", t);
            }
            Log.i(TAG, "ENGINE_ONCREATE");
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Surface s = holder.getSurface();
            boolean valid = s != null && s.isValid();
            Log.i(TAG, "SURFACE_CREATED valid=" + valid);
            paintMessage("Cargando wallpaper...");
            if (valid) main.post(this::startRawPlayer);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            Log.i(TAG, "SURFACE_CHANGED " + width + "x" + height);
            main.post(() -> {
                if (player == null) {
                    startRawPlayer();
                } else {
                    try {
                        player.setSurface(holder.getSurface());
                        if (prepared && visible && !player.isPlaying()) {
                            player.start();
                            Log.i(TAG, "IS_PLAYING_TRUE=" + player.isPlaying());
                        }
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
                    if (v && !player.isPlaying()) {
                        player.start();
                        Log.i(TAG, "IS_PLAYING_TRUE=" + player.isPlaying());
                    } else if (!v && player.isPlaying()) {
                        player.pause();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "visibility toggle failed", t);
                }
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "SURFACE_DESTROYED");
            main.post(this::release);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "ENGINE_DESTROYED");
            main.post(this::release);
            super.onDestroy();
        }

        private void startRawPlayer() {
            try {
                release();
                if (currentHolder == null) {
                    Log.w(TAG, "startRawPlayer: no holder");
                    return;
                }
                Surface surface = currentHolder.getSurface();
                if (surface == null || !surface.isValid()) {
                    Log.w(TAG, "startRawPlayer: surface invalid, skipping");
                    return;
                }

                AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.testwallpaper);
                if (afd == null) {
                    Log.e(TAG, "openRawResourceFd null");
                    paintMessage("RAW no encontrado");
                    return;
                }
                Log.i(TAG, "RAW afd length=" + afd.getLength());

                player = new MediaPlayer();
                player.setSurface(surface);
                player.setLooping(true);
                try { player.setVolume(0f, 0f); } catch (Throwable ignored) {}

                player.setOnPreparedListener(mp -> {
                    prepared = true;
                    Log.i(TAG, "MEDIAPLAYER_PREPARED");
                    try {
                        Surface cur = currentHolder != null ? currentHolder.getSurface() : null;
                        if (cur == null || !cur.isValid()) {
                            Log.w(TAG, "Prepared but surface invalid, deferring");
                            return;
                        }
                        mp.start();
                        Log.i(TAG, "MEDIAPLAYER_STARTED");
                        Log.i(TAG, "IS_PLAYING_TRUE=" + mp.isPlaying());
                    } catch (Throwable t) {
                        Log.e(TAG, "start() failed", t);
                    }
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MEDIAPLAYER_ERROR what=" + what + " extra=" + extra);
                    paintMessage("Error " + what + "/" + extra);
                    return true;
                });
                player.setOnVideoSizeChangedListener((mp, w, h) ->
                        Log.i(TAG, "VIDEO_SIZE " + w + "x" + h));

                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                player.prepareAsync();
                Log.i(TAG, "prepareAsync issued");
            } catch (Throwable t) {
                Log.e(TAG, "startRawPlayer failed", t);
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

        private void release() {
            prepared = false;
            if (player != null) {
                try { if (player.isPlaying()) player.stop(); } catch (Throwable ignored) {}
                try { player.reset(); } catch (Throwable ignored) {}
                try { player.release(); } catch (Throwable ignored) {}
                player = null;
            }
        }
    }
}
