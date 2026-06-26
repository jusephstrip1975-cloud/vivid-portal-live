package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
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
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";
    private static final boolean ENABLE_CANVAS_EMERGENCY_FALLBACK = false;

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
        private String currentPath;
        private long currentVersion = -1L;
        private String rendererUsed = "NONE";
        private boolean triedOriginalFallback = false;
        private SurfaceHolder currentHolder;
        private boolean visible = false;
        private final Handler main = new Handler(Looper.getMainLooper());
        private SharedPreferences prefs;
        private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            Log.i(TAG, "Engine.onCreate");
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            prefs = getApplicationContext()
                .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
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
                        if (player != null) player.setVideoSurface(holder.getSurface());
                        if (fallbackPlayer != null) fallbackPlayer.setSurface(holder.getSurface());
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
                        if (ENABLE_CANVAS_EMERGENCY_FALLBACK && frameRetriever != null && frameLoop != null) {
                            main.post(frameLoop);
                        }
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
                String savedUri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);
                long version = prefs.getLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, 0L);
                Log.i(TAG, "startPlayer prev=" + currentPath + " prevVersion=" + currentVersion
                    + " new=" + path + " newVersion=" + version + " savedUri=" + savedUri);
                if (path == null || !path.equals(currentPath) || version != currentVersion) {
                    triedOriginalFallback = false;
                }
                currentPath = path;
                currentVersion = version;

                if (path == null) {
                    paintMessage("Guarda el vídeo otra vez en la app");
                    return;
                }
                File convertedOutput = new File(path);
                if (!convertedOutput.exists() || convertedOutput.length() <= 0 || !convertedOutput.canRead()) {
                    Log.w(TAG, "Persisted wallpaper file missing or unreadable: " + path);
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

                VideoStats stats = readVideoStats(uri);
                Log.i(TAG, "Wallpaper media stats renderer=prepare inputDurationMs=" + stats.durationMs
                    + " inputFps=" + stats.fps
                    + " inputSize=" + stats.width + "x" + stats.height
                    + " codec=" + stats.videoMime
                    + " bitrate=" + stats.bitrate
                    + " profile=" + stats.profile
                    + " level=" + stats.level
                    + " colorFormat=" + stats.colorFormat
                    + " audio=" + stats.audioMime
                    + " decoder=" + stats.decoderName
                    + " playable=" + stats.playable
                    + " uri=" + uri);
                Log.i(TAG, "RENDERER_USED=PREPARING_NATIVE preferred=MEDIAPLAYER_SAMSUNG exoFallback=true canvasEnabled=" + ENABLE_CANVAS_EMERGENCY_FALLBACK);
                Log.i(TAG, "MediaPlayer primary media item=" + uri + " size=" + sizeForLog);
                lastUri = uri;
                startMediaPlayerFallback(uri);
            } catch (Throwable t) {
                Log.e(TAG, "startPlayer failed, trying native fallback chain", t);
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
                VideoStats stats = readVideoStats(uri);
                Log.i(TAG, "renderer=MediaPlayer start uri=" + uri
                    + " inputDurationMs=" + stats.durationMs
                    + " inputFps=" + stats.fps
                    + " codec=" + stats.videoMime
                    + " bitrate=" + stats.bitrate
                    + " profile=" + stats.profile
                    + " level=" + stats.level
                    + " decoder=" + stats.decoderName
                    + " noManualTimers=true noFrameExtraction=true");
                fallbackPlayer = new MediaPlayer();
                fallbackPlayer.setSurface(currentHolder.getSurface());
                fallbackPlayer.setLooping(true);
                fallbackPlayer.setVolume(0f, 0f);
                fallbackPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error what=" + what + " extra=" + extra
                        + " currentPath=" + currentPath + " triedOriginal=" + triedOriginalFallback);
                    Log.w(TAG, "RENDERER_USED=MEDIAPLAYER_FAILED switchingTo=EXOPLAYER_FALLBACK");
                    main.post(() -> startExoPlayerFallback(uri));
                    return true;
                });
                fallbackPlayer.setOnInfoListener((mp, what, extra) -> {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        rendererUsed = "MEDIAPLAYER";
                        Log.i(TAG, "RENDERER_USED=MEDIAPLAYER");
                    }
                    return false;
                });
                fallbackPlayer.setOnPreparedListener(mp -> {
                    Log.i(TAG, "renderer=MediaPlayer prepared, starting playback");
                    try {
                        mp.start();
                        Log.i(TAG, "renderer=MediaPlayer started currentWallpaperPath=" + currentPath);
                    } catch (Throwable t) {
                        Log.e(TAG, "MediaPlayer start failed", t);
                        return;
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mp.setPlaybackParams(new PlaybackParams().setSpeed(1.0f).setPitch(1.0f));
                            Log.i(TAG, "renderer=MediaPlayer playbackSpeed=" + mp.getPlaybackParams().getSpeed());
                        } else {
                            Log.i(TAG, "renderer=MediaPlayer playbackSpeed=1.0 sdkNoPlaybackParams");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "MediaPlayer setPlaybackParams failed; continuing native realtime playback: " + t.getMessage());
                    }
                });
                fallbackPlayer.setDataSource(getApplicationContext(), uri);
                fallbackPlayer.prepareAsync();
            } catch (Throwable t) {
                Log.e(TAG, "MediaPlayer fallback failed", t);
                startExoPlayerFallback(uri);
            }
        }

        private void startExoPlayerFallback(Uri uri) {
            try {
                releasePlayer();
                if (uri == null) {
                    paintMessage("Vídeo no disponible");
                    return;
                }
                if (currentHolder == null || currentHolder.getSurface() == null
                        || !currentHolder.getSurface().isValid()) {
                    Log.w(TAG, "ExoPlayer fallback: surface not valid");
                    return;
                }
                VideoStats stats = readVideoStats(uri);
                Log.i(TAG, "renderer=ExoPlayer fallback start uri=" + uri
                    + " inputDurationMs=" + stats.durationMs
                    + " inputFps=" + stats.fps
                    + " codec=" + stats.videoMime
                    + " bitrate=" + stats.bitrate
                    + " profile=" + stats.profile
                    + " level=" + stats.level
                    + " decoder=" + stats.decoderName
                    + " playbackSpeed=1.0");

                DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(getApplicationContext())
                    .setEnableDecoderFallback(true);
                player = new ExoPlayer.Builder(getApplicationContext(), renderersFactory).build();
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                player.setVolume(0f);
                player.setPlaybackParameters(new PlaybackParameters(1.0f));
                player.setAudioAttributes(
                        new AudioAttributes.Builder().setUsage(C.USAGE_UNKNOWN).build(),
                        false);
                player.setVideoSurface(currentHolder.getSurface());
                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        Log.i(TAG, "renderer=ExoPlayer state=" + state
                            + " playbackSpeed=" + player.getPlaybackParameters().speed
                            + " currentWallpaperPath=" + currentPath);
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        Log.e(TAG, "ExoPlayer error code=" + error.errorCode
                                + " name=" + error.getErrorCodeName()
                                + " currentPath=" + currentPath
                                + " triedOriginal=" + triedOriginalFallback, error);
                        if (!triedOriginalFallback && tryOriginalFallback()) {
                            return;
                        }
                        if (ENABLE_CANVAS_EMERGENCY_FALLBACK) {
                            Log.w(TAG, "RENDERER_USED=EXOPLAYER_FAILED switchingTo=CANVAS_FALLBACK");
                            main.post(() -> startCanvasFrameFallback(uri));
                        } else {
                            Log.e(TAG, "RENDERER_USED=NONE nativePlaybackFailed=true canvasFallbackDisabled=true");
                            main.post(() -> paintMessage("Vídeo no soportado por el dispositivo"));
                        }
                    }

                    @Override
                    public void onRenderedFirstFrame() {
                        rendererUsed = "EXOPLAYER";
                        Log.i(TAG, "RENDERER_USED=EXOPLAYER_FALLBACK");
                        Log.i(TAG, "renderer=ExoPlayer onRenderedFirstFrame playbackSpeed="
                            + player.getPlaybackParameters().speed
                            + " currentWallpaperPath=" + currentPath);
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
                Log.i(TAG, "ExoPlayer.prepare+play fallback issued playbackSpeed=1.0 noManualTimers=true noFrameExtraction=true");
            } catch (Throwable t) {
                Log.e(TAG, "ExoPlayer fallback failed", t);
                if (!triedOriginalFallback && tryOriginalFallback()) {
                    return;
                }
                if (ENABLE_CANVAS_EMERGENCY_FALLBACK) {
                    startCanvasFrameFallback(uri);
                } else {
                    Log.e(TAG, "RENDERER_USED=NONE nativePlaybackFailed=true canvasFallbackDisabled=true");
                    paintMessage("Vídeo no soportado por el dispositivo");
                }
            }
        }

        /**
         * Last-resort fallback: re-point the wallpaper service to the ORIGINAL (un-transcoded)
         * file if it exists and differs from what we just tried. Returns true if a retry was scheduled.
         */
        private boolean tryOriginalFallback() {
            try {
                if (prefs == null) return false;
                String original = prefs.getString(AetherXLiveWallpaperPlugin.KEY_ORIGINAL_PATH, null);
                Log.i(TAG, "tryOriginalFallback original=" + original + " current=" + currentPath);
                if (original == null) return false;
                File f = new File(original);
                if (!f.exists() || f.length() <= 0 || !f.canRead()) {
                    Log.w(TAG, "tryOriginalFallback: original file not usable size="
                        + (f.exists() ? f.length() : -1));
                    return false;
                }
                if (original.equals(currentPath)) {
                    Log.w(TAG, "tryOriginalFallback: already playing original, nothing to retry");
                    return false;
                }
                triedOriginalFallback = true;
                Log.w(TAG, "RENDERER_USED=RETRY_WITH_ORIGINAL_FILE path=" + original);
                long version = prefs.getLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, 0L) + 1L;
                prefs.edit()
                    .putString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, original)
                    .putLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, version)
                    .commit();
                main.post(() -> {
                    releasePlayer();
                    startPlayer();
                });
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "tryOriginalFallback failed", t);
                return false;
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
                VideoStats stats = readVideoStats(uri);
                Log.w(TAG, "RENDERER_USED=CANVAS_FALLBACK emergencyOnly=true");
                Log.i(TAG, "renderer=Canvas start uri=" + uri
                    + " inputDurationMs=" + stats.durationMs
                    + " inputFps=" + stats.fps);
                frameRetriever = new MediaMetadataRetriever();
                frameRetriever.setDataSource(getApplicationContext(), uri);
                String durationValue = frameRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long parsedDurationUs;
                try {
                    parsedDurationUs = Math.max(1_000_000L, Long.parseLong(durationValue) * 1000L);
                } catch (Throwable ignored) {
                    parsedDurationUs = 5_000_000L;
                }
                final long durationUs = parsedDurationUs;
                final int targetFps = stats.fps >= 50f
                    ? 60
                    : (stats.fps >= 23f ? Math.max(24, Math.round(stats.fps)) : 60);
                final long frameStepUs = Math.max(1L, 1_000_000L / targetFps);
                final long frameDelayMs = Math.max(1L, 1000L / targetFps);
                Log.i(TAG, "renderer=Canvas playbackSpeed=1.0 targetFps=" + targetFps
                    + " frameStepUs=" + frameStepUs
                    + " frameDelayMs=" + frameDelayMs
                    + " durationUs=" + durationUs);
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
                            main.postDelayed(this, frameDelayMs);
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

        private VideoStats readVideoStats(Uri uri) {
            VideoStats stats = new VideoStats();
            if (uri == null) return stats;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            MediaExtractor extractor = new MediaExtractor();
            try {
                retriever.setDataSource(getApplicationContext(), uri);
                stats.width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                stats.height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                stats.durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                stats.fps = parseFloat(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE));

                if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                    extractor.setDataSource(uri.getPath());
                } else {
                    extractor.setDataSource(getApplicationContext(), uri, null);
                }
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.containsKey(MediaFormat.KEY_MIME)
                        ? format.getString(MediaFormat.KEY_MIME)
                        : "";
                    if (mime != null && mime.startsWith("video/")) {
                        stats.videoMime = mime;
                        stats.decoderName = findDecoderName(format, mime);
                        if (stats.fps <= 0f && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            stats.fps = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                        }
                        if (stats.durationMs <= 0 && format.containsKey(MediaFormat.KEY_DURATION)) {
                            stats.durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L;
                        }
                        stats.bitrate = getInteger(format, "bitrate");
                        stats.profile = getInteger(format, "profile");
                        stats.level = getInteger(format, "level");
                        stats.colorFormat = getInteger(format, "color-format");
                        extractor.selectTrack(i);
                        stats.sampleReadable = extractor.readSampleData(java.nio.ByteBuffer.allocate(16 * 1024), 0) >= 0;
                        extractor.unselectTrack(i);
                    } else if (mime != null && mime.startsWith("audio/")) {
                        stats.audioMime = mime;
                    }
                }
                stats.playable = stats.width > 0
                    && stats.height > 0
                    && stats.durationMs > 0
                    && stats.videoMime != null
                    && stats.videoMime.startsWith("video/")
                    && stats.sampleReadable
                    && stats.decoderName != null
                    && !stats.decoderName.isEmpty();
            } catch (Throwable t) {
                Log.e(TAG, "readVideoStats failed uri=" + uri, t);
            } finally {
                try { retriever.release(); } catch (Throwable ignored) {}
                try { extractor.release(); } catch (Throwable ignored) {}
            }
            return stats;
        }

        private String findDecoderName(MediaFormat format, String mime) {
            try {
                MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
                String direct = list.findDecoderForFormat(format);
                if (direct != null && !direct.isEmpty()) return direct;
                for (MediaCodecInfo info : list.getCodecInfos()) {
                    if (info.isEncoder()) continue;
                    String[] types = info.getSupportedTypes();
                    if (types == null) continue;
                    for (String type : types) {
                        if (!mime.equalsIgnoreCase(type)) continue;
                        try {
                            if (info.getCapabilitiesForType(type).isFormatSupported(format)) {
                                return info.getName();
                            }
                        } catch (Throwable ignored) {
                            return info.getName();
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "findDecoderName failed mime=" + mime + " err=" + t.getMessage());
            }
            return "";
        }

        private int getInteger(MediaFormat format, String key) {
            try { return format.containsKey(key) ? format.getInteger(key) : 0; }
            catch (Throwable ignored) { return 0; }
        }

        private int parseInt(String value) {
            try { return value == null ? 0 : Integer.parseInt(value); }
            catch (Throwable ignored) { return 0; }
        }

        private long parseLong(String value) {
            try { return value == null ? 0L : Long.parseLong(value); }
            catch (Throwable ignored) { return 0L; }
        }

        private float parseFloat(String value) {
            try { return value == null ? 0f : Float.parseFloat(value); }
            catch (Throwable ignored) { return 0f; }
        }

        private final class VideoStats {
            long durationMs;
            float fps;
            int width;
            int height;
            int bitrate;
            int profile;
            int level;
            int colorFormat;
            String videoMime = "";
            String audioMime = "";
            String decoderName = "";
            boolean sampleReadable;
            boolean playable;
        }

        private void releasePlayer() {
            Log.i(TAG, "releasePlayer rendererUsed=" + rendererUsed
                + " hasExoPlayer=" + (player != null)
                + " hasMediaPlayer=" + (fallbackPlayer != null)
                + " hasCanvasFallback=" + (frameRetriever != null));
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
