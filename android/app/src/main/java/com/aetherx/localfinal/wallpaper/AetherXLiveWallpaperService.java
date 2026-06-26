package com.aetherx.localfinal.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import java.util.HashSet;
import java.util.Set;

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
        private Uri lastUri;
        private String currentPath;
        private long currentVersion = -1L;
        private String rendererUsed = "NONE";
        private SurfaceHolder currentHolder;
        private boolean visible = false;
        private final Set<String> failedPlaybackPaths = new HashSet<>();
        private boolean preserveFailedPathsOnNextStart = false;
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
                if (player == null && fallbackPlayer == null) {
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
                if (player == null && fallbackPlayer == null) {
                    if (v) startPlayer();
                    return;
                }
                try {
                    if (v) {
                        if (player != null) { player.setPlayWhenReady(true); player.play(); }
                        if (fallbackPlayer != null) fallbackPlayer.start();
                    } else {
                        if (player != null) { player.setPlayWhenReady(false); player.pause(); }
                        if (fallbackPlayer != null && fallbackPlayer.isPlaying()) fallbackPlayer.pause();
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
                    if (preserveFailedPathsOnNextStart) {
                        preserveFailedPathsOnNextStart = false;
                    } else {
                        failedPlaybackPaths.clear();
                    }
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
                    + " estimatedFps=" + stats.estimatedFps
                    + " inputSize=" + stats.width + "x" + stats.height
                    + " codec=" + stats.videoMime
                    + " bitrate=" + stats.bitrate
                    + " profile=" + stats.profile
                    + " level=" + stats.level
                    + " colorFormat=" + stats.colorFormat
                    + " audio=" + stats.audioMime
                    + " decoder=" + stats.decoderName
                    + " playable=" + stats.playable
                    + " rendererCandidate=MediaPlayer playbackSpeed=1.0 droppedFrames=unavailable"
                    + " uri=" + uri);
                Log.i(TAG, "RENDERER_USED=PREPARING_NATIVE preferred=MEDIAPLAYER_SAMSUNG exoFallback=true canvasFallback=false originalPlaybackAllowed=true");
                Log.i(TAG, "MediaPlayer primary media item=" + uri + " size=" + sizeForLog);
                lastUri = uri;
                startMediaPlayerFallback(uri);
            } catch (Throwable t) {
                Log.e(TAG, "startPlayer failed; trying alternate fallback if available", t);
                tryAlternateOrFatal("startPlayer-exception", t);
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
                    + " estimatedFps=" + stats.estimatedFps
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
                    Log.e(TAG, "MEDIAPLAYER_FAILED what=" + what + " extra=" + extra
                        + " currentPath=" + currentPath);
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
                    Log.i(TAG, "MEDIAPLAYER_OK renderer=MediaPlayer prepared, starting playback path=" + currentPath);
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
                            Log.i(TAG, "AETHERX_INTERNAL_PLAYBACK_TEST renderer=MediaPlayer playbackSpeed="
                                + mp.getPlaybackParams().getSpeed()
                                + " durationMs=" + mp.getDuration()
                                + " fps=" + stats.fps
                                + " droppedFrames=unavailable"
                                + " path=" + currentPath);
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
                Log.e(TAG, "MEDIAPLAYER_FAILED setup path=" + currentPath, t);
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
                    + " estimatedFps=" + stats.estimatedFps
                    + " codec=" + stats.videoMime
                    + " bitrate=" + stats.bitrate
                    + " profile=" + stats.profile
                    + " level=" + stats.level
                    + " decoder=" + stats.decoderName
                    + " playbackSpeed=1.0 droppedFrames=unavailable");

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
                                + " currentPath=" + currentPath, error);
                        Log.e(TAG, "EXOPLAYER_FAILED currentPath=" + currentPath
                            + " code=" + error.getErrorCodeName());
                        Log.e(TAG, "RENDERER_USED=NONE nativePlaybackFailed=true canvasFallbackDisabled=true originalPlaybackAllowed=true");
                        main.post(() -> tryAlternateOrFatal(error.getErrorCodeName(), error));
                    }

                    @Override
                    public void onRenderedFirstFrame() {
                        rendererUsed = "EXOPLAYER";
                        Log.i(TAG, "EXOPLAYER_OK currentPath=" + currentPath);
                        Log.i(TAG, "RENDERER_USED=EXOPLAYER_FALLBACK");
                        Log.i(TAG, "renderer=ExoPlayer onRenderedFirstFrame playbackSpeed="
                            + player.getPlaybackParameters().speed
                            + " durationMs=" + player.getDuration()
                            + " fps=" + stats.fps
                            + " droppedFrames=unavailable"
                            + " currentWallpaperPath=" + currentPath);
                        Log.i(TAG, "AETHERX_INTERNAL_PLAYBACK_TEST renderer=ExoPlayer playbackSpeed="
                            + player.getPlaybackParameters().speed
                            + " durationMs=" + player.getDuration()
                            + " fps=" + stats.fps
                            + " droppedFrames=unavailable"
                            + " path=" + currentPath);
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
                Log.e(TAG, "EXOPLAYER_FAILED setup path=" + currentPath, t);
                Log.e(TAG, "RENDERER_USED=NONE nativePlaybackFailed=true canvasFallbackDisabled=true originalPlaybackAllowed=true");
                tryAlternateOrFatal("exo-setup-failed", t);
            }
        }

        private void tryAlternateOrFatal(String reason, Throwable error) {
            try {
                if (prefs == null) {
                    prefs = getApplicationContext()
                        .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                }
                String original = prefs.getString(AetherXLiveWallpaperPlugin.KEY_ORIGINAL_PATH, null);
                String converted = prefs.getString(AetherXLiveWallpaperPlugin.KEY_CONVERTED_PATH, null);
                if (currentPath != null) failedPlaybackPaths.add(currentPath);
                Log.w(TAG, "tryAlternateOrFatal reason=" + reason
                    + " current=" + currentPath
                    + " originalSource=" + original
                    + " convertedCandidate=" + converted
                    + " failedPaths=" + failedPlaybackPaths.size()
                    + " error=" + (error == null ? "none" : error.getMessage()));
                String next = chooseAlternatePath(original, converted);
                if (next == null) {
                    paintMessage("Vídeo no soportado por el dispositivo");
                    return;
                }
                Log.i(TAG, (next.equals(original) ? "USING_ORIGINAL" : "USING_CONVERTED")
                    + " reason=renderer-fallback source=" + next);
                long version = prefs.getLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, 0L) + 1L;
                prefs.edit()
                    .putString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, next)
                    .putLong("video_updated_at", System.currentTimeMillis())
                    .putLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, version)
                    .commit();
                preserveFailedPathsOnNextStart = true;
                releasePlayer();
                startPlayer();
            } catch (Throwable t) {
                Log.e(TAG, "tryAlternateOrFatal failed", t);
                paintMessage("Vídeo no soportado por el dispositivo");
            }
        }

        private String chooseAlternatePath(String original, String converted) {
            String[] candidates;
            if (currentPath != null && currentPath.equals(original)) {
                candidates = new String[] { converted };
            } else if (currentPath != null && currentPath.equals(converted)) {
                candidates = new String[] { original };
            } else {
                candidates = new String[] { original, converted };
            }
            for (String candidate : candidates) {
                if (candidate == null || candidate.equals(currentPath) || failedPlaybackPaths.contains(candidate)) continue;
                File f = new File(candidate);
                if (f.exists() && f.length() > 0 && f.canRead()) return candidate;
                Log.w(TAG, "Alternate playback path not usable path=" + candidate
                    + " exists=" + f.exists()
                    + " size=" + (f.exists() ? f.length() : -1)
                    + " canRead=" + f.canRead());
            }
            return null;
        }

        private void deletePreviousConverted(String previous, String keep) {
            try {
                if (previous == null || previous.equals(keep)) return;
                File old = new File(previous);
                File root = getApplicationContext().getFilesDir();
                if (!old.getCanonicalPath().startsWith(root.getCanonicalPath())) return;
                if (old.exists() && old.delete()) {
                    Log.i(TAG, "Deleted previous converted wallpaper=" + previous);
                }
            } catch (Throwable t) {
                Log.w(TAG, "deletePreviousConverted failed: " + t.getMessage());
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
                        stats.sampleReadable = extractor.readSampleData(java.nio.ByteBuffer.allocate(1024 * 1024), 0) >= 0;
                        try { extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC); } catch (Throwable ignored) {}
                        stats.estimatedFps = estimateSelectedTrackFps(extractor);
                        if (stats.estimatedFps > 0f) stats.fps = stats.estimatedFps;
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

        private float estimateSelectedTrackFps(MediaExtractor extractor) {
            try {
                long previousUs = -1L;
                long totalDeltaUs = 0L;
                int deltas = 0;
                for (int i = 0; i < 120; i++) {
                    long timeUs = extractor.getSampleTime();
                    if (timeUs < 0) break;
                    if (previousUs >= 0 && timeUs > previousUs) {
                        totalDeltaUs += (timeUs - previousUs);
                        deltas++;
                    }
                    previousUs = timeUs;
                    if (!extractor.advance()) break;
                }
                if (deltas <= 0 || totalDeltaUs <= 0L) return 0f;
                return 1_000_000f / (totalDeltaUs / (float) deltas);
            } catch (Throwable t) {
                Log.w(TAG, "estimateSelectedTrackFps failed err=" + t.getMessage());
                return 0f;
            }
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
            float estimatedFps;
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
                + " hasCanvasFallback=false");
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
