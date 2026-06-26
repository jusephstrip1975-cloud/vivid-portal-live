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
import android.os.Environment;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";
    private static final long MIN_VALID_VIDEO_BYTES = 1024L * 1024L;
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
                long version = prefs.getLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, 0L);
                File expectedCurrent = getCurrentWallpaperFile();
                logServicePath("startPlayer-initial", path, expectedCurrent);
                Log.i(TAG, "startPlayer prev=" + currentPath + " prevVersion=" + currentVersion
                    + " new=" + path + " newVersion=" + version
                    + " expectedCurrent=" + expectedCurrent.getAbsolutePath());
                if (!isCurrentPathUsable(path, expectedCurrent, "startPlayer-initial")) {
                    Log.w(TAG, "CURRENT_MP4_MISSING service attempting restore path=" + path);
                    if (attemptRestoreCurrentMp4("service-startPlayer")) {
                        path = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                        version = prefs.getLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, 0L);
                        expectedCurrent = getCurrentWallpaperFile();
                        logServicePath("startPlayer-after-restore", path, expectedCurrent);
                    }
                }
                if (path == null || !path.equals(currentPath) || version != currentVersion) {
                    if (preserveFailedPathsOnNextStart) {
                        preserveFailedPathsOnNextStart = false;
                    }
                }
                currentPath = path;
                currentVersion = version;

                if (path == null) {
                    Log.e(TAG, "CURRENT_MP4_MISSING reason=KEY_VIDEO_PATH_EMPTY ABSOLUTE_PATH=" + expectedCurrent.getAbsolutePath());
                    paintMessage("Archivo de wallpaper no encontrado");
                    return;
                }
                File selectedOutput = new File(path);
                try {
                    String selectedCanonical = selectedOutput.getCanonicalPath();
                    String expectedCanonical = expectedCurrent.getCanonicalPath();
                    if (!selectedCanonical.equals(expectedCanonical)) {
                        Log.e(TAG, "CURRENT_MP4_MISSING reason=KEY_VIDEO_PATH_NOT_CURRENT"
                            + " VIDEO_PATH=" + path
                            + " expected=" + expectedCanonical
                            + " ABSOLUTE_PATH=" + expectedCurrent.getAbsolutePath());
                        paintMessage("Archivo de wallpaper no encontrado");
                        return;
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "CURRENT_MP4_MISSING reason=canonical-path-failed VIDEO_PATH=" + path, t);
                    paintMessage("Archivo de wallpaper no encontrado");
                    return;
                }
                boolean selectedExists = selectedOutput.exists();
                long selectedSize = selectedExists ? selectedOutput.length() : -1L;
                boolean selectedCanRead = selectedExists && selectedOutput.canRead();
                Log.i(TAG, "PATH=" + path);
                Log.i(TAG, "EXISTS=" + selectedExists);
                Log.i(TAG, "CAN_READ=" + selectedCanRead);
                Log.i(TAG, "SIZE=" + selectedSize);
                Log.i(TAG, "ABSOLUTE_PATH=" + selectedOutput.getAbsolutePath());
                Log.i(TAG, "WALLPAPER_SERVICE_PATH=" + selectedOutput.getAbsolutePath());
                if (!selectedExists || !selectedCanRead) {
                    Log.e(TAG, "CURRENT_MP4_MISSING reason=file-missing-or-not-readable VIDEO_PATH=" + path
                        + " FILE_EXISTS=" + selectedExists
                        + " FILE_SIZE=" + selectedSize
                        + " canRead=" + selectedCanRead
                        + " ABSOLUTE_PATH=" + selectedOutput.getAbsolutePath());
                    paintMessage("Archivo de wallpaper no encontrado");
                    return;
                }
                if (selectedSize <= MIN_VALID_VIDEO_BYTES) {
                    Log.e(TAG, "CURRENT_MP4_MISSING reason=file-too-small VIDEO_PATH=" + path
                        + " FILE_EXISTS=" + selectedExists
                        + " FILE_SIZE=" + selectedSize
                        + " minBytes=" + MIN_VALID_VIDEO_BYTES
                        + " ABSOLUTE_PATH=" + selectedOutput.getAbsolutePath());
                    paintMessage("Archivo de wallpaper no encontrado");
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
                    Log.e(TAG, "CURRENT_MP4_MISSING reason=uri-open-failed VIDEO_PATH=" + path
                        + " ABSOLUTE_PATH=" + selectedOutput.getAbsolutePath());
                    paintMessage("Archivo de wallpaper no encontrado");
                    return;
                }

                VideoStats stats = readVideoStats(uri);
                Log.i(TAG, "FILE_DURATION=" + stats.durationMs + " SELECTED_PATH=" + path);
                Log.i(TAG, "FILE_MIME=" + stats.videoMime + " SELECTED_PATH=" + path);
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
                Log.i(TAG, "RENDERER_USED=PREPARING_NATIVE preferred=MEDIAPLAYER_SAMSUNG exoFallback=true canvasFallback=false persistentCurrentOnly=true playbackOrder=MediaPlayerCurrent_ExoCurrent");
                Log.i(TAG, "MediaPlayer primary media item=" + uri + " size=" + sizeForLog);
                lastUri = uri;
                if (testMediaPlayerPrepare(selectedOutput, "SERVICE_SELECTED_BEFORE_RENDER")) {
                    startMediaPlayerFallback(uri);
                } else {
                    Log.w(TAG, "MEDIAPLAYER_FAILED prepare-test-before-render path=" + currentPath
                        + " switchingTo=EXOPLAYER_CURRENT_MP4");
                    startExoPlayerFallback(uri);
                }
            } catch (Throwable t) {
                Log.e(TAG, "startPlayer failed for persistent current.mp4", t);
                fatalPlaybackFailure("startPlayer-exception", t);
            }
        }

        private void logServicePath(String stage, String path, File current) {
            File selected = path == null ? current : new File(path);
            boolean exists = selected.exists();
            boolean canRead = exists && selected.canRead();
            long size = exists ? selected.length() : -1L;
            Log.i(TAG, "WALLPAPER_SERVICE_PATH=" + selected.getAbsolutePath() + " stage=" + stage);
            Log.i(TAG, "PATH=" + path);
            Log.i(TAG, "EXISTS=" + exists);
            Log.i(TAG, "CAN_READ=" + canRead);
            Log.i(TAG, "SIZE=" + size);
            Log.i(TAG, "ABSOLUTE_PATH=" + selected.getAbsolutePath());
        }

        private boolean isCurrentPathUsable(String path, File expectedCurrent, String stage) {
            if (path == null) {
                Log.e(TAG, "CURRENT_MP4_MISSING reason=KEY_VIDEO_PATH_EMPTY stage=" + stage);
                return false;
            }
            File selected = new File(path);
            try {
                if (!selected.getCanonicalPath().equals(expectedCurrent.getCanonicalPath())) {
                    Log.e(TAG, "CURRENT_MP4_MISSING reason=KEY_VIDEO_PATH_NOT_CURRENT stage=" + stage
                        + " PATH=" + path
                        + " expected=" + expectedCurrent.getCanonicalPath());
                    return false;
                }
            } catch (Throwable t) {
                Log.e(TAG, "CURRENT_MP4_MISSING reason=canonical-check-failed stage=" + stage
                    + " PATH=" + path, t);
                return false;
            }
            boolean exists = selected.exists();
            boolean canRead = exists && selected.canRead();
            long size = exists ? selected.length() : -1L;
            if (!exists || !canRead || size <= MIN_VALID_VIDEO_BYTES) {
                Log.e(TAG, "CURRENT_MP4_MISSING reason=file-unusable stage=" + stage
                    + " PATH=" + path
                    + " EXISTS=" + exists
                    + " CAN_READ=" + canRead
                    + " SIZE=" + size
                    + " ABSOLUTE_PATH=" + selected.getAbsolutePath());
                return false;
            }
            return true;
        }

        private boolean attemptRestoreCurrentMp4(String stage) {
            try {
                if (prefs == null) {
                    prefs = getApplicationContext()
                        .getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                }
                File current = getCurrentWallpaperFile();
                String lastPath = prefs.getString(AetherXLiveWallpaperPlugin.KEY_LAST_VALID_VIDEO_PATH, null);
                File lastValid = lastPath == null ? getLastValidWallpaperFile() : new File(lastPath);
                Log.w(TAG, "CURRENT_MP4_RESTORE_START stage=" + stage
                    + " current=" + current.getAbsolutePath()
                    + " currentExists=" + current.exists()
                    + " currentCanRead=" + current.canRead()
                    + " currentSize=" + (current.exists() ? current.length() : -1)
                    + " lastValid=" + lastValid.getAbsolutePath()
                    + " lastValidExists=" + lastValid.exists()
                    + " lastValidCanRead=" + lastValid.canRead()
                    + " lastValidSize=" + (lastValid.exists() ? lastValid.length() : -1));
                if (!lastValid.exists() || !lastValid.canRead() || lastValid.length() <= MIN_VALID_VIDEO_BYTES) {
                    Log.e(TAG, "CURRENT_MP4_RESTORE_FAILED reason=last-valid-unusable stage=" + stage);
                    return false;
                }
                copyFile(lastValid, current);
                if (!current.exists() || !current.canRead() || current.length() <= MIN_VALID_VIDEO_BYTES) {
                    Log.e(TAG, "CURRENT_MP4_RESTORE_FAILED reason=current-after-copy-unusable stage=" + stage
                        + " PATH=" + current.getAbsolutePath()
                        + " EXISTS=" + current.exists()
                        + " CAN_READ=" + current.canRead()
                        + " SIZE=" + (current.exists() ? current.length() : -1));
                    return false;
                }
                long newVersion = prefs.getLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, 0L) + 1L;
                prefs.edit()
                    .putString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, current.getAbsolutePath())
                    .putLong(AetherXLiveWallpaperPlugin.KEY_VIDEO_VERSION, newVersion)
                    .putLong("video_updated_at", System.currentTimeMillis())
                    .commit();
                Log.i(TAG, "CURRENT_MP4_RECREATED stage=" + stage
                    + " PATH=" + current.getAbsolutePath()
                    + " EXISTS=" + current.exists()
                    + " CAN_READ=" + current.canRead()
                    + " SIZE=" + current.length()
                    + " ABSOLUTE_PATH=" + current.getAbsolutePath()
                    + " version=" + newVersion);
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "CURRENT_MP4_RESTORE_FAILED stage=" + stage, t);
                return false;
            }
        }

        private void copyFile(File from, File to) throws Exception {
            File parent = to.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new Exception("mkdirs-failed:" + parent.getAbsolutePath());
            }
            try (FileInputStream in = new FileInputStream(from);
                 FileOutputStream out = new FileOutputStream(to, false)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                out.getFD().sync();
            }
        }

        private boolean testMediaPlayerPrepare(File file, String label) {
            MediaPlayer mp = null;
            try {
                if (file == null || !file.exists() || file.length() <= MIN_VALID_VIDEO_BYTES || !file.canRead()) {
                    Log.e(TAG, "MEDIAPLAYER_PREPARE_FAILED label=" + label
                        + " reason=file-not-usable"
                        + " SELECTED_PATH=" + (file == null ? "null" : file.getAbsolutePath())
                        + " FILE_EXISTS=" + (file != null && file.exists())
                        + " FILE_SIZE=" + (file != null && file.exists() ? file.length() : -1)
                        + " canRead=" + (file != null && file.canRead()));
                    return false;
                }
                mp = new MediaPlayer();
                mp.setDataSource(file.getAbsolutePath());
                mp.setVolume(0f, 0f);
                mp.prepare();
                Log.i(TAG, "MEDIAPLAYER_PREPARE_OK label=" + label
                    + " SELECTED_PATH=" + file.getAbsolutePath()
                    + " durationMs=" + mp.getDuration());
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "MEDIAPLAYER_PREPARE_FAILED label=" + label
                    + " SELECTED_PATH=" + (file == null ? "null" : file.getAbsolutePath()), t);
                return false;
            } finally {
                if (mp != null) {
                    try { mp.release(); } catch (Throwable ignored) {}
                }
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
                    Log.w(TAG, "RENDERER_USED=MEDIAPLAYER_FAILED switchingTo=EXOPLAYER_CURRENT_MP4");
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
                    Log.i(TAG, "MEDIAPLAYER_PREPARE_OK renderer=MediaPlayer asyncPrepared SELECTED_PATH=" + currentPath
                        + " durationMs=" + mp.getDuration());
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
                Log.e(TAG, "MEDIAPLAYER_PREPARE_FAILED setup SELECTED_PATH=" + currentPath, t);
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
                        Log.e(TAG, "EXOPLAYER_SOURCE_FAILED SELECTED_PATH=" + currentPath
                            + " code=" + error.getErrorCodeName()
                            + " message=" + error.getMessage());
                        Log.e(TAG, "EXOPLAYER_FAILED currentPath=" + currentPath
                            + " code=" + error.getErrorCodeName());
                        Log.e(TAG, "RENDERER_USED=NONE nativePlaybackFailed=true canvasFallbackDisabled=true persistentCurrentOnly=true");
                        main.post(() -> fatalPlaybackFailure(error.getErrorCodeName(), error));
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
                Log.i(TAG, "EXOPLAYER_SOURCE_OK SELECTED_PATH=" + currentPath + " uri=" + uri);
                player.prepare();
                player.setPlayWhenReady(true);
                player.play();
                Log.i(TAG, "ExoPlayer.prepare+play fallback issued playbackSpeed=1.0 noManualTimers=true noFrameExtraction=true");
            } catch (Throwable t) {
                Log.e(TAG, "EXOPLAYER_SOURCE_FAILED SELECTED_PATH=" + currentPath, t);
                Log.e(TAG, "EXOPLAYER_FAILED setup path=" + currentPath, t);
                Log.e(TAG, "RENDERER_USED=NONE nativePlaybackFailed=true canvasFallbackDisabled=true persistentCurrentOnly=true");
                fatalPlaybackFailure("exo-setup-failed", t);
            }
        }

        private void fatalPlaybackFailure(String reason, Throwable error) {
            File current = getCurrentWallpaperFile();
            Log.e(TAG, "CURRENT_MP4_MISSING playback-failed reason=" + reason
                + " VIDEO_PATH=" + currentPath
                + " FILE_EXISTS=" + current.exists()
                + " CAN_READ=" + current.canRead()
                + " FILE_SIZE=" + (current.exists() ? current.length() : -1)
                + " ABSOLUTE_PATH=" + current.getAbsolutePath(), error);
            paintMessage("Archivo de wallpaper no encontrado");
        }

        private File getCurrentWallpaperFile() {
            return new File(getWallpaperDir(), "current.mp4");
        }

        private File getLastValidWallpaperFile() {
            return new File(getWallpaperDir(), "last-valid.mp4");
        }

        private File getWallpaperDir() {
            File moviesDir = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (moviesDir == null) {
                File externalRoot = Environment.getExternalStorageDirectory();
                moviesDir = new File(externalRoot, "Android/data/" + getApplicationContext().getPackageName() + "/files/Movies");
                Log.w(TAG, "external-movies-dir-unavailable using-package-external-path=" + moviesDir.getAbsolutePath());
            }
            File dir = new File(moviesDir, "AetherX");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "wallpaper-dir-mkdirs-failed path=" + dir.getAbsolutePath());
            }
            return dir;
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
