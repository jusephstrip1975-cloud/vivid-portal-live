package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
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
import androidx.media3.exoplayer.DefaultRenderersFactory;
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
        private MediaPlayer fallbackPlayer;
        private MediaMetadataRetriever frameRetriever;
        private Runnable frameLoop;
        private Uri lastUri;
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
                if (player == null && fallbackPlayer == null && frameRetriever == null) {
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
                if (player == null && fallbackPlayer == null && frameRetriever == null) {
                    if (v) startPlayer();
                    return;
                }
                try {
                    if (v) {
                        if (player != null) { player.setPlayWhenReady(true); player.play(); }
                        if (fallbackPlayer != null) fallbackPlayer.start();
                        if (frameRetriever != null && frameLoop != null) main.post(frameLoop);
                    } else {
                        if (player != null) { player.setPlayWhenReady(false); player.pause(); }
                        if (fallbackPlayer != null && fallbackPlayer.isPlaying()) fallbackPlayer.pause();
                        if (frameLoop != null) main.removeCallbacks(frameLoop);
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
                String savedUri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);
                Log.i(TAG, "startPlayer savedWallpaperVideo=" + path + " savedUri=" + savedUri);

                File convertedOutput = WallpaperVideoTranscoder.getOutputFile(getApplicationContext());
                if (convertedOutput.exists() && convertedOutput.length() > 0 && convertedOutput.canRead()) {
                    path = convertedOutput.getAbsolutePath();
                    Log.i(TAG, "Using mandatory Samsung-safe converted path=" + path
                        + " size=" + convertedOutput.length());
                } else {
                    Log.w(TAG, "Samsung-safe converted output missing; refusing to play original unsupported video");
                    paintMessage("Guarda el vídeo otra vez en la app");
                    return;
                }

                Uri uri = null;
                long sizeForLog = -1;

                if (path != null) {
                    File f = new File(path);
                    boolean exists = f.exists();
                    boolean canRead = f.canRead();
                    sizeForLog = exists ? f.length() : -1;
                    Log.i(TAG, "File exists=" + exists + " canRead=" + canRead + " size=" + sizeForLog
                        + " path=" + path);
                    if (exists && sizeForLog > 0) {
                        // Verify we can actually open it
                        try (android.os.ParcelFileDescriptor pfd =
                                 android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)) {
                            Log.i(TAG, "ContentResolver openFileDescriptor (file) ok fd=" + (pfd != null));
                            uri = Uri.fromFile(f);
                        } catch (Exception e) {
                            Log.w(TAG, "openFileDescriptor on file path failed: " + e.getMessage());
                        }
                    }
                }
                if (uri == null) {
                    paintMessage("Vídeo convertido no encontrado");
                    return;
                }

                Log.i(TAG, "ExoPlayer media item=" + uri + " size=" + sizeForLog);
                lastUri = uri;

                DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(getApplicationContext())
                    .setEnableDecoderFallback(true);
                player = new ExoPlayer.Builder(getApplicationContext(), renderersFactory).build();
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
                        Log.i(TAG, "Falling back to native MediaPlayer due to ExoPlayer failure");
                        main.post(() -> startMediaPlayerFallback(lastUri));
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
                Log.e(TAG, "startPlayer failed, trying MediaPlayer fallback", t);
                startMediaPlayerFallback(lastUri);
            }
        }

        private void startMediaPlayerFallback(Uri uri) {
            try {
                releasePlayer();
                if (uri == null) {
                    paintMessage("Vídeo no disponible");
                    return;
                }
                if (currentHolder == null || currentHolder.getSurface() == null
                        || !currentHolder.getSurface().isValid()) {
                    Log.w(TAG, "MediaPlayer fallback: surface not valid");
                    return;
                }
                Log.i(TAG, "MediaPlayer fallback start uri=" + uri);
                fallbackPlayer = new MediaPlayer();
                fallbackPlayer.setSurface(currentHolder.getSurface());
                fallbackPlayer.setLooping(true);
                fallbackPlayer.setVolume(0f, 0f);
                fallbackPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error what=" + what + " extra=" + extra);
                    main.post(() -> startCanvasFrameFallback(uri));
                    return true;
                });
                fallbackPlayer.setOnPreparedListener(mp -> {
                    Log.i(TAG, "MediaPlayer prepared, starting playback");
                    try { mp.start(); } catch (Throwable t) { Log.e(TAG, "MediaPlayer start failed", t); }
                });
                fallbackPlayer.setDataSource(getApplicationContext(), uri);
                fallbackPlayer.prepareAsync();
            } catch (Throwable t) {
                Log.e(TAG, "MediaPlayer fallback failed", t);
                startCanvasFrameFallback(uri);
            }
        }

        private void startCanvasFrameFallback(Uri uri) {
            try {
                releasePlayer();
                if (uri == null) {
                    paintMessage("Vídeo no disponible");
                    return;
                }
                if (currentHolder == null || currentHolder.getSurface() == null
                        || !currentHolder.getSurface().isValid()) {
                    Log.w(TAG, "Canvas frame fallback: surface not valid");
                    return;
                }
                Log.i(TAG, "Canvas frame fallback start uri=" + uri);
                frameRetriever = new MediaMetadataRetriever();
                frameRetriever.setDataSource(getApplicationContext(), uri);
                String durationValue = frameRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                final long durationUs;
                try {
                    durationUs = Math.max(1_000_000L, Long.parseLong(durationValue) * 1000L);
                } catch (Throwable ignored) {
                    durationUs = 5_000_000L;
                }
                final long frameStepUs = 83_333L; // 12 fps: stable for wallpaper preview surfaces.
                final long[] positionUs = new long[] {0L};
                frameLoop = new Runnable() {
                    @Override
                    public void run() {
                        if (frameRetriever == null || currentHolder == null || !visible) return;
                        try {
                            Bitmap frame = frameRetriever.getFrameAtTime(positionUs[0], MediaMetadataRetriever.OPTION_CLOSEST);
                            if (frame != null) {
                                drawFrame(frame);
                                frame.recycle();
                            }
                            positionUs[0] = (positionUs[0] + frameStepUs) % durationUs;
                            main.postDelayed(this, 83L);
                        } catch (Throwable t) {
                            Log.e(TAG, "Canvas frame fallback failed while drawing", t);
                            paintMessage("Vídeo no soportado por el dispositivo");
                        }
                    }
                };
                frameLoop.run();
            } catch (Throwable t) {
                Log.e(TAG, "Canvas frame fallback setup failed", t);
                paintMessage("Vídeo no soportado por el dispositivo");
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

        private void drawFrame(Bitmap frame) {
            Canvas c = null;
            try {
                if (currentHolder == null) return;
                c = currentHolder.lockCanvas();
                if (c == null) return;
                c.drawColor(Color.BLACK);
                int canvasW = c.getWidth();
                int canvasH = c.getHeight();
                int frameW = frame.getWidth();
                int frameH = frame.getHeight();
                float scale = Math.max(canvasW / (float) frameW, canvasH / (float) frameH);
                int outW = Math.round(frameW * scale);
                int outH = Math.round(frameH * scale);
                int left = (canvasW - outW) / 2;
                int top = (canvasH - outH) / 2;
                c.drawBitmap(frame, null, new Rect(left, top, left + outW, top + outH), null);
            } finally {
                try {
                    if (c != null && currentHolder != null) currentHolder.unlockCanvasAndPost(c);
                } catch (Throwable ignored) {}
            }
        }

        private void releasePlayer() {
            if (frameLoop != null) {
                main.removeCallbacks(frameLoop);
                frameLoop = null;
            }
            if (frameRetriever != null) {
                try { frameRetriever.release(); } catch (Throwable ignored) {}
                frameRetriever = null;
            }
            if (player != null) {
                try {
                    player.stop();
                    player.clearVideoSurface();
                    player.release();
                } catch (Throwable ignored) {
                }
                player = null;
            }
            if (fallbackPlayer != null) {
                try {
                    if (fallbackPlayer.isPlaying()) fallbackPlayer.stop();
                    fallbackPlayer.release();
                } catch (Throwable ignored) {
                }
                fallbackPlayer = null;
            }
        }
    }
}
