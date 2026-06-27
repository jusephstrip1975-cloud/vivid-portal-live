package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.aetherx.livewallpaper.BuildConfig;

import java.io.File;

/**
 * AetherX Live Wallpaper Service — MediaPlayer-only engine.
 *
 * Versión 2.1.3-surface-gated.
 *
 * Samsung OneUI fix for mediaplayer-error:-38:
 *  - setSurface() BEFORE prepareAsync()
 *  - start() ONLY when both surfaceReady && playerReady
 *  - 300ms delay before start() (Samsung surface settle)
 */
public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";
    private static final long MIN_VALID_VIDEO_BYTES = 1024L * 1024L;
    public static final String SERVICE_ENGINE = "MEDIAPLAYER_ONLY";

    @Override
    public Engine onCreateEngine() {
        Log.i(TAG, "SERVICE_ENGINE=" + SERVICE_ENGINE
            + " EXOPLAYER_ENABLED=false"
            + " APP_BUILD_VERSION=" + BuildConfig.AETHERX_BUILD_VERSION
            + " " + BuildConfig.AETHERX_BUILD_MARKER);
        return new VideoEngine();
    }

    private class VideoEngine extends Engine {

        private MediaPlayer mediaPlayer;
        private String currentPath;
        private SurfaceHolder currentHolder;
        private boolean surfaceReady = false;
        private boolean playerReady = false;
        private boolean visible = false;
        private final Handler main = new Handler(Looper.getMainLooper());
        private SharedPreferences prefs;
        private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            Log.i(TAG, "Engine.onCreate SERVICE_ENGINE=" + SERVICE_ENGINE);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            prefs = getApplicationContext()
                .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            persistEngineFlags(false);
            prefsListener = (sp, key) -> {
                if (AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH.equals(key)
                    || AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION.equals(key)) {
                    Log.i(TAG, "Prefs changed key=" + key + " -> reloading wallpaper engine");
                    main.post(() -> {
                        releasePlayer();
                        preparePlayer();
                    });
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            surfaceReady = true;
            Surface s = holder.getSurface();
            Log.i(TAG, "onSurfaceCreated SURFACE_READY=true surfaceValid=" + (s != null && s.isValid()));
            paintMessage("Cargando vídeo...");
            main.post(() -> {
                if (mediaPlayer == null) {
                    preparePlayer();
                } else {
                    try { mediaPlayer.setSurface(holder.getSurface()); } catch (Throwable ignored) {}
                    tryStartPlayback();
                }
            });
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            surfaceReady = true;
            Log.i(TAG, "onSurfaceChanged " + width + "x" + height + " SURFACE_READY=true");
            main.post(() -> {
                if (mediaPlayer == null) {
                    preparePlayer();
                } else {
                    try { mediaPlayer.setSurface(holder.getSurface()); } catch (Throwable ignored) {}
                    tryStartPlayback();
                }
            });
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            visible = v;
            Log.i(TAG, "onVisibilityChanged visible=" + v + " hasPlayer=" + (mediaPlayer != null));
            main.post(() -> {
                if (mediaPlayer == null) {
                    if (v) preparePlayer();
                    return;
                }
                try {
                    if (v) {
                        tryStartPlayback();
                    } else {
                        if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                    }
                } catch (Throwable ignored) {}
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "onSurfaceDestroyed");
            surfaceReady = false;
            main.post(this::releasePlayer);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "Engine.onDestroy");
            try {
                if (prefs != null && prefsListener != null) {
                    prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
                }
            } catch (Throwable ignored) {}
            main.post(this::releasePlayer);
            super.onDestroy();
        }

        private void preparePlayer() {
            try {
                releasePlayer();
                if (!surfaceReady || currentHolder == null || currentHolder.getSurface() == null
                        || !currentHolder.getSurface().isValid()) {
                    Log.w(TAG, "preparePlayer: surface not ready");
                    return;
                }

                if (prefs == null) {
                    prefs = getApplicationContext()
                        .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                }
                String path = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                Log.i(TAG, "SERVICE_READ_KEY_VIDEO_PATH=" + path);

                if (path == null || path.isEmpty()) {
                    File fallback = getCurrentWallpaperFile();
                    if (fallback.exists() && fallback.length() > MIN_VALID_VIDEO_BYTES) {
                        String abs = fallback.getAbsolutePath();
                        prefs.edit().putString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, abs).commit();
                        path = abs;
                        Log.i(TAG, "SERVICE_VIDEO_RECOVERED=true path=" + abs);
                    } else {
                        Log.e(TAG, "SERVICE_VIDEO_RECOVERED=false path=" + fallback.getAbsolutePath());
                        prefs.edit().putString("last_service_error", "KEY_VIDEO_PATH_EMPTY_AND_NO_DISK_FILE").commit();
                        paintMessage("Archivo de wallpaper no encontrado");
                        return;
                    }
                }

                File file = new File(path);
                boolean exists = file.exists();
                boolean canRead = exists && file.canRead();
                long size = exists ? file.length() : -1L;
                Log.i(TAG, "SERVICE_FILE PATH=" + path + " EXISTS=" + exists
                    + " CAN_READ=" + canRead + " SIZE=" + size);
                if (!exists || !canRead || size <= MIN_VALID_VIDEO_BYTES) {
                    String reason = !exists ? "file-missing" : !canRead ? "no-read" : "file-too-small";
                    prefs.edit().putString("last_service_error", "CURRENT_MP4_INVALID:" + reason).commit();
                    paintMessage("Archivo de wallpaper no encontrado");
                    return;
                }

                currentPath = path;
                playerReady = false;

                Log.i(TAG, "SERVICE_ENGINE=" + SERVICE_ENGINE + " preparing MediaPlayer path=" + file.getAbsolutePath());
                persistEngineFlags(false);

                mediaPlayer = new MediaPlayer();
                // CRITICAL ORDER for Samsung: setSurface BEFORE setDataSource + prepareAsync.
                mediaPlayer.setSurface(currentHolder.getSurface());
                mediaPlayer.setVolume(0f, 0f);
                mediaPlayer.setLooping(true);

                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MEDIAPLAYER_ERROR_CODE=" + what + " MEDIAPLAYER_ERROR_EXTRA=" + extra
                        + " path=" + currentPath);
                    try {
                        prefs.edit()
                            .putString("last_service_error", "mediaplayer-error:" + what + "/" + extra)
                            .putBoolean("mediaplayer_started", false)
                            .commit();
                    } catch (Throwable ignored) {}
                    main.post(() -> fatalPlaybackFailure("mediaplayer-error:" + what + "/" + extra, null));
                    return true;
                });

                mediaPlayer.setOnPreparedListener(mp -> {
                    playerReady = true;
                    Log.i(TAG, "MEDIAPLAYER_ON_PREPARED PLAYER_READY=true durationMs=" + safeDuration(mp));
                    // Samsung surface-settle delay before start.
                    main.postDelayed(this::tryStartPlayback, 300L);
                });

                mediaPlayer.setDataSource(file.getAbsolutePath());
                Log.i(TAG, "MEDIAPLAYER_SET_DATASOURCE_OK path=" + file.getAbsolutePath());
                mediaPlayer.prepareAsync();
                Log.i(TAG, "MEDIAPLAYER_PREPARE_ASYNC");
            } catch (Throwable t) {
                Log.e(TAG, "preparePlayer failed", t);
                fatalPlaybackFailure("preparePlayer-exception", t);
            }
        }

        private void tryStartPlayback() {
            Log.i(TAG, "TRY_START_PLAYBACK surfaceReady=" + surfaceReady
                + " playerReady=" + playerReady + " hasPlayer=" + (mediaPlayer != null));
            if (mediaPlayer == null || !surfaceReady || !playerReady) return;
            try {
                if (currentHolder != null && currentHolder.getSurface() != null
                        && currentHolder.getSurface().isValid()) {
                    try { mediaPlayer.setSurface(currentHolder.getSurface()); } catch (Throwable ignored) {}
                }
                mediaPlayer.setLooping(true);
                if (!mediaPlayer.isPlaying()) {
                    Log.i(TAG, "MEDIA_PLAYER_START_CALLED");
                    mediaPlayer.start();
                    boolean playing = false;
                    try { playing = mediaPlayer.isPlaying(); } catch (Throwable ignored) {}
                    Log.i(TAG, "MEDIA_PLAYER_IS_PLAYING=" + playing);
                    persistEngineFlags(true);
                }
            } catch (Throwable t) {
                Log.e(TAG, "tryStartPlayback failed", t);
                try {
                    prefs.edit().putString("last_service_error", "start-failed:" + t).commit();
                } catch (Throwable ignored) {}
            }
        }

        private long safeDuration(MediaPlayer mp) {
            try { return mp.getDuration(); } catch (Throwable t) { return -1L; }
        }

        private void persistEngineFlags(boolean started) {
            try {
                if (prefs == null) return;
                prefs.edit()
                    .putString("player_engine", "MediaPlayer")
                    .putBoolean("exoplayer_enabled", false)
                    .putBoolean("mediaplayer_started", started)
                    .commit();
            } catch (Throwable ignored) {}
        }

        private void fatalPlaybackFailure(String reason, Throwable error) {
            File current = getCurrentWallpaperFile();
            Log.e(TAG, "PLAYBACK_FAILED reason=" + reason
                + " VIDEO_PATH=" + currentPath
                + " FILE_EXISTS=" + current.exists()
                + " FILE_SIZE=" + (current.exists() ? current.length() : -1), error);
            try {
                if (prefs == null) {
                    prefs = getApplicationContext()
                        .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                }
                prefs.edit().putString("last_service_error",
                    reason + (error == null ? "" : ": " + error.getMessage()))
                    .putBoolean("mediaplayer_started", false)
                    .commit();
            } catch (Throwable ignored) {}
            if (!current.exists() || current.length() <= MIN_VALID_VIDEO_BYTES) {
                paintMessage("Archivo de wallpaper no encontrado");
            } else {
                paintMessage("No se pudo reproducir el vídeo");
            }
        }

        private File getCurrentWallpaperFile() {
            return new File(getWallpaperDir(), "current.mp4");
        }

        private File getWallpaperDir() {
            File moviesDir = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (moviesDir == null) throw new IllegalStateException("external-movies-dir-unavailable");
            File dir = new File(moviesDir, "AetherX");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "wallpaper-dir-mkdirs-failed path=" + dir.getAbsolutePath());
            }
            return dir;
        }

        private void paintMessage(String text) {
            try {
                if (currentHolder == null) return;
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
            playerReady = false;
            if (mediaPlayer != null) {
                Log.i(TAG, "releasePlayer MediaPlayer release");
                try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch (Throwable ignored) {}
                try { mediaPlayer.release(); } catch (Throwable ignored) {}
                mediaPlayer = null;
                persistEngineFlags(false);
            }
        }
    }
}
