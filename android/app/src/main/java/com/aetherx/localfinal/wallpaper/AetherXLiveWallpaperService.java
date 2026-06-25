package com.aetherx.localfinal.wallpaper;

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

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";

    @Override
    public Engine onCreateEngine() {
        Log.i(TAG, "onCreateEngine");
        return new VideoEngine();
    }

    @OptIn(markerClass = UnstableApi.class)
    private class VideoEngine extends Engine {

        private ExoPlayer player;
        private SurfaceHolder currentHolder;
        private boolean visible = false;
        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            Log.i(TAG, "Engine.onCreate");
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Surface s = holder.getSurface();
            Log.i(TAG, "onSurfaceCreated surfaceValid=" + (s != null && s.isValid()));
            paintLoading("Cargando vídeo...");
            main.post(this::startPlayer);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            Log.i(TAG, "onSurfaceChanged " + width + "x" + height + " format=" + format);
            main.post(() -> {
                if (player == null) {
                    startPlayer();
                } else {
                    try {
                        player.setVideoSurface(holder.getSurface());
                    } catch (Throwable t) {
                        Log.e(TAG, "setVideoSurface on change failed", t);
                    }
                }
            });
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            visible = v;
            Log.i(TAG, "onVisibilityChanged visible=" + v + " player=" + (player != null));
            main.post(() -> {
                if (player == null) {
                    if (v) startPlayer();
                    return;
                }
                if (v) {
                    player.setPlayWhenReady(true);
                    player.play();
                } else {
                    player.setPlayWhenReady(false);
                    player.pause();
                }
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

                SharedPreferences prefs = getApplicationContext()
                        .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                String path = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                Log.i(TAG, "startPlayer path=" + path);

                if (path == null) {
                    paintMessage("Selecciona un vídeo en la app");
                    return;
                }
                File f = new File(path);
                if (!f.exists() || f.length() == 0) {
                    Log.w(TAG, "video file missing or empty: " + path);
                    paintMessage("Vídeo no encontrado");
                    return;
                }
                if (!f.canRead()) {
                    Log.w(TAG, "video file not readable: " + path);
                }

                Uri uri = Uri.fromFile(f);
                Log.i(TAG, "Building ExoPlayer for uri=" + uri + " size=" + f.length());

                player = new ExoPlayer.Builder(getApplicationContext()).build();
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                player.setVolume(0f);
                player.setAudioAttributes(
                        new AudioAttributes.Builder().setUsage(C.USAGE_UNKNOWN).build(),
                        false);
                player.setVideoSurface(currentHolder.getSurface());
                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        Log.i(TAG, "ExoPlayer state=" + state);
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        Log.e(TAG, "ExoPlayer error code=" + error.errorCode
                                + " name=" + error.getErrorCodeName(), error);
                        paintMessage("Error: " + error.getErrorCodeName());
                    }

                    @Override
                    public void onRenderedFirstFrame() {
                        Log.i(TAG, "ExoPlayer onRenderedFirstFrame");
                    }

                    @Override
                    public void onVideoSizeChanged(VideoSize videoSize) {
                        Log.i(TAG, "ExoPlayer videoSize " + videoSize.width + "x" + videoSize.height);
                    }
                });
                player.setMediaItem(MediaItem.fromUri(uri));
                player.prepare();
                player.setPlayWhenReady(true);
                player.play();
                Log.i(TAG, "ExoPlayer.prepare+play issued");
            } catch (Throwable t) {
                Log.e(TAG, "startPlayer failed", t);
                paintMessage("Fallo al iniciar reproductor");
            }
        }

        private void paintLoading(String text) {
            paintMessage(text);
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
            if (player != null) {
                try {
                    player.stop();
                    player.clearVideoSurface();
                    player.release();
                } catch (Throwable ignored) {
                }
                player = null;
            }
        }
    }
}
