package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.C;
import androidx.media3.common.AudioAttributes;
import androidx.media3.exoplayer.ExoPlayer;

import com.aetherx.livewallpaper.R;

import java.io.File;

/**
 * Plan B: ExoPlayer-backed live wallpaper for Samsung One UI.
 *
 * Why ExoPlayer instead of MediaPlayer:
 *   MediaPlayer.setDisplay() -> updateSurfaceScreenOn() -> setKeepScreenOn()
 *   throws UnsupportedOperationException("Wallpapers do not support keep screen on")
 *   inside WallpaperService.Engine. ExoPlayer's setVideoSurface(Surface) never
 *   touches that code path.
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

    private void clearNativeFailureState() {
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            p.edit()
                .putString(AetherXLiveWallpaperPlugin.KEY_LAST_NATIVE_EXCEPTION, "(none)")
                .putString(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_ERROR, "(none)")
                .apply();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        recordStep("SERVICE_ONCREATE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreate");
    }

    @Override
    public Engine onCreateEngine() {
        recordStep("ON_CREATE_ENGINE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreateEngine");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "onCreateEngine");
        return new ExoVideoEngine();
    }

    private class ExoVideoEngine extends Engine {
        private ExoPlayer player;
        private SurfaceHolder currentHolder;
        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            recordStep("ENGINE_CREATED");
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "engineOnCreate");
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Surface s = holder.getSurface();
            boolean valid = s != null && s.isValid();
            recordStep("ON_SURFACE_CREATED valid=" + valid);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceCreated valid=" + valid);
            paintMessage("Cargando wallpaper...");
            if (valid) main.post(this::startExoPlayer);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceChanged " + width + "x" + height);
            main.post(() -> {
                if (player == null) {
                    startExoPlayer();
                } else {
                    try { player.setVideoSurface(holder.getSurface()); } catch (Throwable ignored) {}
                }
            });
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            main.post(() -> {
                if (player == null) return;
                try {
                    if (v) player.play();
                    else player.pause();
                } catch (Throwable t) {
                    Log.e(TAG, "visibility toggle failed", t);
                }
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            recordStep("SURFACE_DESTROYED");
            main.post(this::releasePlayer);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            recordStep("ENGINE_DESTROYED");
            main.post(this::releasePlayer);
            super.onDestroy();
        }

        private void startExoPlayer() {
            try {
                clearNativeFailureState();
                releasePlayer();

                SurfaceHolder holder = currentHolder;
                if (holder == null) {
                    recordStep("EXO_HOLDER_NULL");
                    return;
                }
                Surface surface = holder.getSurface();
                if (surface == null || !surface.isValid()) {
                    recordStep("EXO_SURFACE_INVALID");
                    return;
                }
                recordStep("EXO_SURFACE_VALID");
                clearSurface(holder);

                // Resolve MediaItem: selected file -> selected content URI -> RAW fallback.
                MediaItem item = null;
                SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                String selectedPath = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                String selectedUri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);

                if (selectedPath != null) {
                    File f = new File(selectedPath);
                    if (f.exists() && f.canRead() && f.length() > 0) {
                        item = MediaItem.fromUri(Uri.fromFile(f));
                        recordStep("EXO_SOURCE_FILE len=" + f.length());
                    }
                }
                if (item == null && selectedUri != null && !selectedUri.isEmpty()) {
                    item = MediaItem.fromUri(Uri.parse(selectedUri));
                    recordStep("EXO_SOURCE_URI " + selectedUri);
                }
                if (item == null) {
                    Uri raw = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.testwallpaper);
                    item = MediaItem.fromUri(raw);
                    recordStep("EXO_SOURCE_RAW");
                }

                ExoPlayer p = new ExoPlayer.Builder(getApplicationContext()).build();
                recordStep("EXO_PLAYER_CREATED");

                AudioAttributes muted = new AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build();
                p.setAudioAttributes(muted, false);
                p.setVolume(0f);
                p.setRepeatMode(Player.REPEAT_MODE_ALL);
                p.setPlayWhenReady(true);

                // setVideoSurface never calls setKeepScreenOn — safe for WallpaperService.
                p.setVideoSurface(surface);
                recordStep("EXO_SURFACE_ATTACHED");

                p.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        switch (state) {
                            case Player.STATE_BUFFERING: recordStep("EXO_BUFFERING"); break;
                            case Player.STATE_READY:     recordStep("EXO_READY"); break;
                            case Player.STATE_ENDED:     recordStep("EXO_ENDED"); break;
                            case Player.STATE_IDLE:      recordStep("EXO_IDLE"); break;
                        }
                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        if (isPlaying) recordStep("EXO_PLAYING");
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        persistNativeException("EXO_PLAYER_ERROR code=" + error.errorCode
                            + " name=" + error.getErrorCodeName(), error);
                        try {
                            SharedPreferences prefs2 = getSharedPreferences(
                                AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                            prefs2.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_ERROR,
                                System.currentTimeMillis() + " " + error.getErrorCodeName()).apply();
                        } catch (Throwable ignored) {}
                        paintMessage("Error: " + error.getErrorCodeName());
                    }
                });

                p.setMediaItem(item);
                recordStep("EXO_MEDIA_ITEM_SET");
                p.prepare();
                recordStep("EXO_PREPARE_CALLED");

                player = p;
            } catch (Throwable t) {
                persistNativeException("EXO_START_EXCEPTION", t);
                paintMessage("Fallo: " + t.getClass().getSimpleName());
            }
        }

        private void clearSurface(SurfaceHolder holder) {
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) c.drawColor(Color.BLACK);
            } catch (Throwable t) {
                Log.w(TAG, "clearSurface failed", t);
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (Throwable ignored) {}
                }
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
            if (player != null) {
                try { player.clearVideoSurface(); } catch (Throwable ignored) {}
                try { player.stop(); } catch (Throwable ignored) {}
                try { player.release(); } catch (Throwable ignored) {}
                player = null;
            }
        }
    }
}
