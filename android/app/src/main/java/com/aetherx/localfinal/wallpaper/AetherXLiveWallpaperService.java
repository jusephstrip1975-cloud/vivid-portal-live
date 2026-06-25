package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";

    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    @OptIn(markerClass = UnstableApi.class)
    private class VideoEngine extends Engine {

        private ExoPlayer player;
        private SurfaceHolder currentHolder;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            startPlayer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            if (player == null) startPlayer();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (player == null) return;
            if (visible) {
                player.play();
            } else {
                player.pause();
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            releasePlayer();
        }

        @Override
        public void onDestroy() {
            releasePlayer();
            super.onDestroy();
        }

        private void startPlayer() {
            try {
                releasePlayer();
                SharedPreferences prefs = getApplicationContext()
                        .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                String path = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                if (path == null) {
                    paintBlack();
                    return;
                }
                File f = new File(path);
                if (!f.exists() || f.length() == 0) {
                    paintBlack();
                    return;
                }
                Uri uri = Uri.fromFile(f);
                player = new ExoPlayer.Builder(getApplicationContext()).build();
                player.setVideoSurface(currentHolder.getSurface());
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                player.setVolume(0f);
                player.setAudioAttributes(
                        new androidx.media3.common.AudioAttributes.Builder()
                                .setUsage(C.USAGE_UNKNOWN)
                                .build(),
                        false);
                player.setMediaItem(MediaItem.fromUri(uri));
                player.prepare();
                player.play();
            } catch (Throwable t) {
                Log.e(TAG, "startPlayer failed", t);
                paintBlack();
            }
        }

        private void paintBlack() {
            try {
                if (currentHolder == null) return;
                Canvas c = currentHolder.lockCanvas();
                if (c != null) {
                    c.drawColor(Color.BLACK);
                    currentHolder.unlockCanvasAndPost(c);
                }
            } catch (Throwable ignored) {
            }
        }

        private void releasePlayer() {
            if (player != null) {
                try {
                    player.release();
                } catch (Throwable ignored) {
                }
                player = null;
            }
        }
    }
}
