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
 * AetherX Live Wallpaper Service — MEDIAPLAYER-ONLY engine.
 *
 * Versión 2.1.2-mediaplayer-only.
 *
 * Samsung OneUI rechaza MediaCodecVideoRenderer (ExoPlayer/Media3) dentro del
 * WallpaperService con ERROR_CODE_DECODER_INIT_FAILED aunque el MP4 sea válido.
 * Por eso este servicio NO usa ExoPlayer, Media3, MediaCodecVideoRenderer,
 * DefaultRenderersFactory, ProgressiveMediaSource ni PlayerView.
 *
 * Solo android.media.MediaPlayer.
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
        private long currentVersion = -1L;
        private SurfaceHolder currentHolder;
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
                        startPlayer();
                    });
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Surface s = holder.getSurface();
            Log.i(TAG, "onSurfaceCreated surfaceValid=" + (s != null && s.isValid()));
            paintMessage("Cargando vídeo...");
            main.post(this::startPlayer);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            Log.i(TAG, "onSurfaceChanged " + width + "x" + height);
            main.post(() -> {
                if (mediaPlayer == null) {
                    startPlayer();
                } else {
                    try {
                        mediaPlayer.setSurface(holder.getSurface());
                    } catch (Throwable t) {
                        Log.e(TAG, "setSurface on change failed", t);
                    }
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
                    if (v) startPlayer();
                    return;
                }
                try {
                    if (v) {
                        if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                    } else {
                        if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                    }
                } catch (Throwable ignored) {}
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "onSurfaceDestroyed");
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

        private void startPlayer() {
            try {
                releasePlayer();
                if (currentHolder == null || currentHolder.getSurface() == null
                        || !currentHolder.getSurface().isValid()) {
                    Log.w(TAG, "startPlayer: surface not valid yet");
                    return;
                }

                if (prefs == null) {
                    prefs = getApplicationContext()
                        .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                }
                String path = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                long version = prefs.getLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, 0L);
                Log.i(TAG, "SERVICE_READ_KEY_VIDEO_PATH=" + path);

                // AUTO-RECOVERY: if prefs say null but the canonical current.mp4 exists, use it.
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
                    String reason = !exists ? "file-missing"
                        : !canRead ? "no-read"
                        : "file-too-small";
                    Log.e(TAG, "CURRENT_MP4_INVALID reason=" + reason + " path=" + path + " size=" + size);
                    prefs.edit().putString("last_service_error", "CURRENT_MP4_INVALID:" + reason).commit();
                    paintMessage("Archivo de wallpaper no encontrado");
                    return;
                }

                currentPath = path;
                currentVersion = version;
                startMediaPlayer(file);
            } catch (Throwable t) {
                Log.e(TAG, "startPlayer failed", t);
                fatalPlaybackFailure("startPlayer-exception", t);
            }
        }

        private void startMediaPlayer(File file) {
            try {
                Log.i(TAG, "SERVICE_ENGINE=" + SERVICE_ENGINE + " starting MediaPlayer path=" + file.getAbsolutePath());
                persistEngineFlags(false);
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setSurface(currentHolder.getSurface());
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0f, 0f);
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MEDIAPLAYER_ERROR_CODE=" + what + " MEDIAPLAYER_ERROR_EXTRA=" + extra
                        + " path=" + currentPath);
                    try {
                        prefs.edit()
                            .putString("last_service_error",
                                "MEDIAPLAYER_ERROR what=" + what + " extra=" + extra)
                            .putBoolean("mediaplayer_started", false)
                            .commit();
                    } catch (Throwable ignored) {}
                    main.post(() -> fatalPlaybackFailure("mediaplayer-error:" + what + "/" + extra, null));
                    return true;
                });
                mediaPlayer.setOnPreparedListener(mp -> {
                    Log.i(TAG, "MEDIAPLAYER_ON_PREPARED durationMs=" + safeDuration(mp));
                    try {
                        mp.start();
                        Log.i(TAG, "MEDIAPLAYER_STARTED path=" + currentPath);
                        persistEngineFlags(true);
                    } catch (Throwable t) {
                        Log.e(TAG, "MEDIAPLAYER_START_FAILED", t);
                        fatalPlaybackFailure("mediaplayer-start-failed", t);
                    }
                });
                mediaPlayer.setDataSource(file.getAbsolutePath());
                Log.i(TAG, "MEDIAPLAYER_SET_DATASOURCE_OK path=" + file.getAbsolutePath());
                mediaPlayer.prepareAsync();
                Log.i(TAG, "MEDIAPLAYER_PREPARE_ASYNC");
            } catch (Throwable t) {
                Log.e(TAG, "MEDIAPLAYER_SETUP_FAILED path=" + (file == null ? "null" : file.getAbsolutePath()), t);
                fatalPlaybackFailure("mediaplayer-setup-failed", t);
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
            if (moviesDir == null) {
                throw new IllegalStateException("external-movies-dir-unavailable");
            }
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
            } catch (Throwable ignored) {
            }
        }

        private void releasePlayer() {
            if (mediaPlayer != null) {
                Log.i(TAG, "releasePlayer MediaPlayer release");
                try {
                    if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                } catch (Throwable ignored) {}
                try { mediaPlayer.release(); } catch (Throwable ignored) {}
                mediaPlayer = null;
                persistEngineFlags(false);
            }
        }
    }
}
