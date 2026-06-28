package com.aetherx.localfinal.wallpaper;

import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import com.aetherx.localfinal.R;

/**
 * Samsung OEM diagnostic build:
 * - No ExoPlayer, no Media3, no Transformer.
 * - No downloads, no FFmpeg, no cache/files dir, no external Uri.
 * - Plays exclusively res/raw/testwallpaper.mp4 via MediaPlayer + SurfaceHolder.
 */
public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";

    @Override
    public Engine onCreateEngine() {
        Log.i(TAG, "ENGINE_CREATED");
        return new RawVideoEngine();
    }

    private class RawVideoEngine extends Engine {
        private MediaPlayer player;
        private SurfaceHolder currentHolder;
        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Log.i(TAG, "SURFACE_CREATED valid=" + (holder.getSurface() != null && holder.getSurface().isValid()));
            paintMessage("Cargando RAW test...");
            main.post(this::startRawPlayer);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            Log.i(TAG, "SURFACE_CHANGED " + width + "x" + height);
            if (player == null) main.post(this::startRawPlayer);
            else {
                try { player.setSurface(holder.getSurface()); } catch (Throwable ignored) {}
            }
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            Log.i(TAG, "VISIBILITY=" + v);
            main.post(() -> {
                if (player == null) {
                    if (v) startRawPlayer();
                    return;
                }
                try {
                    if (v) { player.start(); Log.i(TAG, "IS_PLAYING_TRUE=" + player.isPlaying()); }
                    else if (player.isPlaying()) player.pause();
                } catch (Throwable t) { Log.e(TAG, "visibility toggle failed", t); }
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
                if (currentHolder == null || currentHolder.getSurface() == null
                        || !currentHolder.getSurface().isValid()) {
                    Log.w(TAG, "startRawPlayer: surface not valid");
                    return;
                }

                AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.testwallpaper);
                if (afd == null) {
                    Log.e(TAG, "openRawResourceFd returned null for R.raw.testwallpaper");
                    paintMessage("RAW no encontrado");
                    return;
                }
                Log.i(TAG, "RAW afd length=" + afd.getLength() + " start=" + afd.getStartOffset());

                player = new MediaPlayer();
                player.setSurface(currentHolder.getSurface());
                player.setLooping(true);
                try { player.setVolume(0f, 0f); } catch (Throwable ignored) {}

                player.setOnPreparedListener(mp -> {
                    Log.i(TAG, "MEDIAPLAYER_PREPARED");
                    try {
                        mp.start();
                        Log.i(TAG, "MEDIAPLAYER_STARTED");
                        Log.i(TAG, "IS_PLAYING_TRUE=" + mp.isPlaying());
                    } catch (Throwable t) {
                        Log.e(TAG, "start() failed", t);
                    }
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MEDIAPLAYER_ERROR what=" + what + " extra=" + extra);
                    paintMessage("Error MediaPlayer " + what + "/" + extra);
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
                paintMessage("Fallo RAW: " + t.getClass().getSimpleName());
            }
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

        private void release() {
            if (player != null) {
                try { if (player.isPlaying()) player.stop(); } catch (Throwable ignored) {}
                try { player.release(); } catch (Throwable ignored) {}
                player = null;
            }
        }
    }
}
