package com.aetherx.livewallpaper.wallpaper;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.aetherx.livewallpaper.R;

import java.io.File;
import java.io.FileInputStream;


/**
 * Samsung One UI hardened WallpaperService.
 * Records every lifecycle step into SharedPreferences for diagnostics.
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

    private void recordError(String key, Throwable t) {
        if (t == null) return;
        Log.e(TAG, key, t);
        try {
            SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String msg = t.getClass().getSimpleName() + ": " + t.getMessage() + "\n" + Log.getStackTraceString(t);
            p.edit().putString(key, System.currentTimeMillis() + " " + msg).apply();
        } catch (Throwable ignored) {}
    }

    private void persistStep(String step) {
        recordStep(step);
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
        Log.i(TAG, "SERVICE_ONCREATE");
        recordStep("SERVICE_ONCREATE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreate");
    }

    @Override
    public Engine onCreateEngine() {
        Log.i(TAG, "ON_CREATE_ENGINE");
        recordStep("ON_CREATE_ENGINE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreateEngine");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "onCreateEngine");
        return new RawVideoEngine();
    }

    private class RawVideoEngine extends Engine {
        private MediaPlayer player;
        private SurfaceHolder currentHolder;
        private boolean prepared = false;
        private boolean visible = false;
        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            // NO setFixedSize: Samsung One UI 7 (SDK 35+) rechaza tamaños fijos
            // y deja el wallpaper en negro. Dejamos que el sistema gestione el tamaño.
            Log.i(TAG, "ENGINE_CREATED");
            recordStep("ENGINE_CREATED");
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "engineOnCreate");
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Surface s = holder.getSurface();
            boolean valid = s != null && s.isValid();
            Log.i(TAG, "ON_SURFACE_CREATED valid=" + valid);
            recordStep("ON_SURFACE_CREATED valid=" + valid);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceCreated valid=" + valid);
            paintMessage("Cargando wallpaper...");
            if (valid) main.post(this::startRawPlayer);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            Log.i(TAG, "SURFACE_CHANGED " + width + "x" + height);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceChanged " + width + "x" + height);
            main.post(() -> {
                if (player == null) {
                    startRawPlayer();
                }
            });
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            visible = v;
            Log.i(TAG, "VISIBILITY=" + v);
            main.post(() -> {
                if (player == null || !prepared) return;
                try {
                    if (!v) player.pause();
                } catch (Throwable t) {
                    Log.e(TAG, "visibility toggle failed", t);
                }
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            Log.i(TAG, "SURFACE_DESTROYED");
            main.post(this::releasePlayer);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            Log.i(TAG, "ENGINE_DESTROYED");
            main.post(this::releasePlayer);
            super.onDestroy();
        }

        private void startRawPlayer() {
            AssetFileDescriptor afd = null;
            ParcelFileDescriptor selectedPfd = null;
            try {
                clearNativeFailureState();
                releasePlayer();

                SurfaceHolder holder = currentHolder;
                if (holder == null) {
                    persistStep("MEDIA_HOLDER_NULL");
                    return;
                }

                Surface surface = holder.getSurface();
                if (surface == null || !surface.isValid()) {
                    persistStep("MEDIA_SURFACE_INVALID");
                    return;
                }
                persistStep("MEDIA_SURFACE_VALID");

                clearSurface(holder);
                persistStep("MEDIA_SURFACE_CLEARED");

                // === ORDEN CORRECTO PARA SAMSUNG ONE UI ===
                // 1. new MediaPlayer   (Idle)
                // 2. setAudioAttrs / setLooping / setVolume (Idle-safe)
                // 3. setDataSource     (-> Initialized)
                // 4. setDisplay(holder) sobre el SurfaceHolder (NO setSurface + setScreenOnWhilePlaying)
                // 5. setOnPrepared / setOnError
                // 6. prepareAsync      (-> Preparing)
                // 7. onPrepared -> start (-> Started)

                player = new MediaPlayer();
                persistStep("MEDIA_PLAYER_CREATED");

                player.setLooping(true);
                player.setVolume(0f, 0f);
                persistStep("MEDIA_AUDIO_MUTED");

                SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                String selectedPath = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                String selectedUri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);
                File selectedFile = selectedPath == null ? null : new File(selectedPath);
                boolean sourceSet = false;

                if (selectedFile != null && selectedFile.exists() && selectedFile.canRead() && selectedFile.length() > 0) {
                    // Abrir con ParcelFileDescriptor: más robusto en Samsung One UI 7 que
                    // pasar la ruta como String. El WallpaperService corre en el mismo UID
                    // pero a veces en un proceso separado del picker, y algunos SoC Exynos/Snapdragon
                    // Samsung fallan setDataSource(String) sobre filesDir con IllegalStateException.
                    try {
                        selectedPfd = ParcelFileDescriptor.open(selectedFile, ParcelFileDescriptor.MODE_READ_ONLY);
                        if (selectedPfd != null) {
                            player.setDataSource(
                                selectedPfd.getFileDescriptor(),
                                0L,
                                selectedFile.length()
                            );
                            persistStep("MEDIA_DATASOURCE_SET_SELECTED_PFD len=" + selectedFile.length());
                            sourceSet = true;
                        } else {
                            persistStep("MEDIA_SELECTED_PFD_NULL");
                        }
                    } catch (Throwable t) {
                        persistNativeException("MEDIA_SELECTED_PFD_FAIL", t);
                    }
                } else if (selectedUri != null && !selectedUri.isEmpty()) {
                    persistStep("MEDIA_DATASOURCE_TRY_URI " + selectedUri);
                    try {
                        Uri uri = Uri.parse(selectedUri);
                        ContentResolver resolver = getContentResolver();
                        selectedPfd = resolver.openFileDescriptor(uri, "r");
                        if (selectedPfd != null) {
                            player.setDataSource(selectedPfd.getFileDescriptor());
                            persistStep("MEDIA_DATASOURCE_SET_URI_FD");
                            sourceSet = true;
                        } else {
                            persistStep("MEDIA_URI_PFD_NULL");
                        }
                    } catch (SecurityException se) {
                        persistNativeException("MEDIA_URI_SECURITY", se);
                    } catch (Throwable t) {
                        persistNativeException("MEDIA_URI_OPEN_FAIL", t);
                    }
                    if (!sourceSet) {
                        try {
                            player.setDataSource(getApplicationContext(), Uri.parse(selectedUri));
                            persistStep("MEDIA_DATASOURCE_SET_URI_CONTEXT");
                            sourceSet = true;
                        } catch (Throwable t) {
                            persistNativeException("MEDIA_URI_CONTEXT_FAIL", t);
                        }
                    }
                }

                if (!sourceSet) {
                    persistStep("MEDIA_SELECTED_FILE_MISSING_USE_RAW");
                    afd = getResources().openRawResourceFd(R.raw.testwallpaper);
                    if (afd == null) {
                        persistStep("MEDIA_RAW_FD_NULL");
                        persistNativeException("MEDIA_RAW_FD_NULL");
                        return;
                    }
                    persistStep("MEDIA_RAW_FD_OPENED len=" + afd.getDeclaredLength());
                    player.setDataSource(
                        afd.getFileDescriptor(),
                        afd.getStartOffset(),
                        afd.getDeclaredLength()
                    );
                    persistStep("MEDIA_DATASOURCE_SET_RAW_FD");
                }

                // setDisplay(holder) tras setDataSource: en Samsung One UI 7 esto
                // conecta el output al SurfaceHolder de forma estable.
                player.setDisplay(holder);
                persistStep("MEDIA_DISPLAY_ATTACHED");

                player.setOnPreparedListener(mp -> {
                    prepared = true;
                    persistStep("MEDIA_PREPARED");
                    try {
                        mp.start();
                        persistStep("MEDIA_STARTED");
                    } catch (Throwable t) {
                        persistNativeException("MEDIA_START_EXCEPTION", t);
                    }
                });
                player.setOnErrorListener((mp, what, extra) -> {
                    String msg = "MEDIA_ERROR what=" + what + " extra=" + extra;
                    persistStep("MEDIA_ERROR");
                    persistNativeException(msg);
                    try {
                        SharedPreferences p = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                        p.edit().putString(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_ERROR,
                            System.currentTimeMillis() + " " + msg).apply();
                    } catch (Throwable ignored) {}
                    paintMessage("Error " + what + "/" + extra);
                    return true;
                });

                player.prepareAsync();
                persistStep("MEDIA_PREPARE_ASYNC");
            } catch (Throwable t) {
                persistNativeException("MEDIA_START_RAW_PLAYER_EXCEPTION", t);
                paintMessage("Fallo: " + t.getClass().getSimpleName());
            } finally {
                if (afd != null) {
                    try { afd.close(); } catch (Throwable ignored) {}
                }
                // Nota: NO cerramos selectedPfd aquí. MediaPlayer necesita el FD vivo
                // durante prepareAsync (asíncrono). Se cierra en releasePlayer().
                if (selectedPfd != null) {
                    currentPfd = selectedPfd;
                }
            }
        }

        private ParcelFileDescriptor currentPfd;

        private void clearSurface(SurfaceHolder holder) {
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    c.drawColor(Color.BLACK);
                }
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
            prepared = false;
            if (player != null) {
                try { player.setOnPreparedListener(null); } catch (Throwable ignored) {}
                try { player.setOnErrorListener(null); } catch (Throwable ignored) {}
                try { player.stop(); } catch (Throwable ignored) {}
                try { player.release(); } catch (Throwable ignored) {}
                player = null;
            }
        }
    }
}
