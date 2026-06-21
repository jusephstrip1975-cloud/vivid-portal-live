package com.aetherx.livewallpaper;

import android.media.MediaPlayer;
import android.service.wallpaper.WallpaperService;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.io.File;

public class AetherXVideoWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    private class VideoEngine extends Engine {
        private MediaPlayer mediaPlayer;
        private SurfaceHolder surfaceHolder;
        private boolean visible = false;
        private boolean prepared = false;
        private long loadedLastModified = -1L;
        private long loadedLength = -1L;
        private Surface activeSurface;

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceHolder = holder;
            visible = isVisible();
            startOrResume();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceHolder = holder;
            visible = isVisible();
            startOrResume();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            stopVideo();
            surfaceHolder = null;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                startOrResume();
            } else {
                pauseVideo();
            }
        }

        @Override
        public void onDestroy() {
            stopVideo();
            super.onDestroy();
        }

        private void startOrResume() {
            if (!visible || surfaceHolder == null || !surfaceHolder.getSurface().isValid()) return;
            File file = new File(getApplicationContext().getFilesDir(), AetherXLiveWallpaperPlugin.VIDEO_FILE);
            if (!file.exists() || file.length() == 0) return;

            Surface surface = surfaceHolder.getSurface();
            boolean fileChanged = file.lastModified() != loadedLastModified || file.length() != loadedLength;
            boolean surfaceChanged = activeSurface == null || activeSurface != surface;
            if (mediaPlayer == null || fileChanged || surfaceChanged) {
                startVideo(surfaceHolder, file);
                return;
            }

            if (prepared) {
                try {
                    if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                } catch (Exception ignored) {}
            }
        }

        private void startVideo(SurfaceHolder holder, File file) {
            stopVideo();

            try {
                MediaPlayer player = new MediaPlayer();
                mediaPlayer = player;
                prepared = false;
                player.setDataSource(file.getAbsolutePath());
                activeSurface = holder.getSurface();
                player.setSurface(activeSurface);
                player.setLooping(true);
                player.setVolume(0f, 0f);
                player.setScreenOnWhilePlaying(false);
                player.setOnErrorListener((mp, what, extra) -> {
                    stopVideo();
                    return true;
                });
                player.setOnCompletionListener(mp -> {
                    try {
                        mp.seekTo(0);
                        if (visible) mp.start();
                    } catch (Exception ignored) {}
                });
                player.setOnPreparedListener(mp -> {
                    if (mediaPlayer != mp) return;
                    prepared = true;
                    loadedLastModified = file.lastModified();
                    loadedLength = file.length();
                    if (visible) {
                        try { mp.start(); } catch (Exception ignored) {}
                    }
                });
                player.prepareAsync();
            } catch (Exception e) {
                stopVideo();
            }
        }

        private void pauseVideo() {
            if (mediaPlayer == null || !prepared) return;
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
            } catch (Exception ignored) {}
        }

        private void stopVideo() {
            if (mediaPlayer == null) return;
            prepared = false;
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {}
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }
}