package com.aetherx.livewallpaper;

import android.media.MediaPlayer;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import java.io.File;

public class AetherXVideoWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    private class VideoEngine extends Engine {
        private MediaPlayer mediaPlayer;

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            startVideo(holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            stopVideo();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (mediaPlayer == null) return;
            if (visible) {
                mediaPlayer.start();
            } else {
                mediaPlayer.pause();
            }
        }

        @Override
        public void onDestroy() {
            stopVideo();
            super.onDestroy();
        }

        private void startVideo(SurfaceHolder holder) {
            stopVideo();
            File file = new File(getApplicationContext().getFilesDir(), AetherXLiveWallpaperPlugin.VIDEO_FILE);
            if (!file.exists() || file.length() == 0) return;

            try {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(file.getAbsolutePath());
                mediaPlayer.setSurface(holder.getSurface());
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(0f, 0f);
                mediaPlayer.setOnPreparedListener(MediaPlayer::start);
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                stopVideo();
            }
        }

        private void stopVideo() {
            if (mediaPlayer == null) return;
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}