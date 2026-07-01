package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.aetherx.livewallpaper.R;

import java.io.File;

/**
 * Plan D: frame blitter. Samsung SDK 36 rejects using the WallpaperService
 * Surface as a direct video decoder output in MediaCodec.configure(...), so we
 * do not attach MediaPlayer/ExoPlayer/MediaCodec to the wallpaper surface at
 * all. We decode individual frames offscreen with MediaMetadataRetriever and
 * paint them to the wallpaper Canvas. It is less efficient, but it bypasses the
 * Samsung direct-surface decoder failure and proves rendering on home/lock.
 */
public class AetherXLiveWallpaperService extends WallpaperService {

    private static final String TAG = "AetherXLiveWP";
    private static final long FRAME_INTERVAL_MS = 42L; // ~24 FPS target; scaled decode keeps CPU low.

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
        recordStep("SERVICE_ONCREATE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreate");
    }

    @Override
    public Engine onCreateEngine() {
        recordStep("ON_CREATE_ENGINE");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SERVICE_EVENT, "onCreateEngine");
        recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "onCreateEngine");
        return new CodecEngine();
    }

    private class CodecEngine extends Engine {
        private SurfaceHolder currentHolder;
        private FrameBlitterThread renderer;
        private volatile boolean visible = false;
        private int surfaceWidth = 0;
        private int surfaceHeight = 0;
        private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            recordStep("ENGINE_CREATED");
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "engineOnCreate");
            registerPrefsListener();
        }

        private void registerPrefsListener() {
            try {
                SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                prefsListener = (sp, key) -> {
                    if (AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH.equals(key)
                        || AetherXLiveWallpaperPlugin.KEY_VIDEO_URI.equals(key)) {
                        recordStep("PREFS_VIDEO_CHANGED key=" + key);
                        if (currentHolder != null) startRenderer(currentHolder);
                    }
                };
                prefs.registerOnSharedPreferenceChangeListener(prefsListener);
            } catch (Throwable t) {
                persistNativeException("PREFS_LISTENER_REGISTER_FAIL", t);
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            Surface s = holder.getSurface();
            boolean valid = s != null && s.isValid();
            recordStep("ON_SURFACE_CREATED valid=" + valid);
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceCreated valid=" + valid);
            paintMessage("Cargando wallpaper...");
            if (valid) startRenderer(holder);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            surfaceWidth = width;
            surfaceHeight = height;
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceChanged " + width + "x" + height);
            Surface s = holder.getSurface();
            if (renderer == null && s != null && s.isValid()) startRenderer(holder);
            else if (renderer != null) renderer.setTargetSize(width, height);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            visible = v;
            if (renderer != null) renderer.setPaused(!v);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            recordStep("SURFACE_DESTROYED");
            stopRenderer();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            recordStep("ENGINE_DESTROYED");
            stopRenderer();
            try {
                if (prefsListener != null) {
                    getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE)
                        .unregisterOnSharedPreferenceChangeListener(prefsListener);
                    prefsListener = null;
                }
            } catch (Throwable ignored) {}
            super.onDestroy();
        }

        private void startRenderer(SurfaceHolder holder) {
            stopRenderer();
            clearNativeFailureState();
            clearSurface(holder);
            renderer = new FrameBlitterThread(holder);
            renderer.setTargetSize(surfaceWidth, surfaceHeight);
            renderer.setPaused(false);
            renderer.start();
        }

        private void stopRenderer() {
            if (renderer != null) {
                renderer.requestStop();
                renderer = null;
            }
        }

        private void clearSurface(SurfaceHolder holder) {
            if (holder == null) return;
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) c.drawColor(Color.BLACK);
            } catch (Throwable ignored) {
            } finally {
                if (c != null) try { holder.unlockCanvasAndPost(c); } catch (Throwable ignored) {}
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
    }

    /**
     * Decodes frames offscreen and draws them to the WallpaperService Canvas.
     * No direct decoder output Surface is used anywhere in this path.
     */
    private class FrameBlitterThread extends Thread {
        private final SurfaceHolder holder;
        private volatile boolean stopRequested = false;
        private volatile boolean paused = false;
        private volatile int targetW = 0;
        private volatile int targetH = 0;

        FrameBlitterThread(SurfaceHolder holder) {
            super("AetherXFrameBlitterThread");
            this.holder = holder;
        }

        void requestStop() {
            stopRequested = true;
            interrupt();
        }
        void setPaused(boolean p) { paused = p; }
        void setTargetSize(int w, int h) { targetW = w; targetH = h; }

        @Override
        public void run() {
            MediaMetadataRetriever retriever = null;
            AssetFileDescriptor afd = null;
            String activePath = null;
            String activeUri = null;
            long durationMs = 8000L;
            long playStartMs = SystemClock.uptimeMillis();
            long lastPositionMs = 0L;
            boolean firstFrameDrawn = false;
            boolean nullFrameLogged = false;
            long lastPrefsCheckMs = 0L;

            try {
                // Initial source load.
                Object[] loaded = openSource(null, null);
                retriever = (MediaMetadataRetriever) loaded[0];
                afd = (AssetFileDescriptor) loaded[1];
                activePath = (String) loaded[2];
                activeUri = (String) loaded[3];
                durationMs = (Long) loaded[4];
                if (retriever == null) return;

                while (!stopRequested) {
                    // Cross-process prefs poll (app process writes, service process reads).
                    long nowMs = SystemClock.uptimeMillis();
                    if (nowMs - lastPrefsCheckMs > 500L) {
                        lastPrefsCheckMs = nowMs;
                        SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                        String newPath = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                        String newUri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);
                        boolean pathChanged = !equalsNullable(newPath, activePath);
                        boolean uriChanged = !equalsNullable(newUri, activeUri) && (newPath == null);
                        if (pathChanged || uriChanged) {
                            recordStep("SOURCE_RELOAD_DETECTED path=" + newPath);
                            try { retriever.release(); } catch (Throwable ignored) {}
                            try { if (afd != null) afd.close(); } catch (Throwable ignored) {}
                            retriever = null; afd = null;
                            Object[] r = openSource(newPath, newUri);
                            retriever = (MediaMetadataRetriever) r[0];
                            afd = (AssetFileDescriptor) r[1];
                            activePath = (String) r[2];
                            activeUri = (String) r[3];
                            durationMs = (Long) r[4];
                            if (retriever == null) { sleepQuietly(500L); continue; }
                            playStartMs = SystemClock.uptimeMillis();
                            firstFrameDrawn = false;
                            nullFrameLogged = false;
                        }
                    }

                    if (paused) {
                        playStartMs = SystemClock.uptimeMillis() - lastPositionMs;
                        sleepQuietly(80L);
                        continue;
                    }

                    Surface surface = holder.getSurface();
                    if (surface == null || !surface.isValid()) {
                        sleepQuietly(80L);
                        continue;
                    }

                    long frameStart = SystemClock.uptimeMillis();
                    lastPositionMs = (frameStart - playStartMs) % durationMs;
                    Bitmap frame = null;
                    int tw = targetW, th = targetH;
                    if (tw > 720) { th = (int) (th * (720.0f / tw)); tw = 720; }
                    try {
                        if (tw > 0 && th > 0) {
                            frame = retriever.getScaledFrameAtTime(
                                lastPositionMs * 1000L,
                                MediaMetadataRetriever.OPTION_CLOSEST,
                                tw, th
                            );
                        } else {
                            frame = retriever.getFrameAtTime(lastPositionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST);
                        }
                        if (frame == null) {
                            frame = retriever.getFrameAtTime(lastPositionMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        }
                    } catch (Throwable t) {
                        persistNativeException("FRAME_DECODE_FAIL", t);
                    }

                    if (frame == null) {
                        if (!nullFrameLogged) { nullFrameLogged = true; recordStep("FRAME_NULL"); }
                        sleepQuietly(FRAME_INTERVAL_MS);
                        continue;
                    }

                    boolean drawn = drawFrame(holder, frame);
                    frame.recycle();
                    if (drawn && !firstFrameDrawn) {
                        firstFrameDrawn = true;
                        recordStep("FRAME_FIRST_DRAW");
                    }

                    long elapsed = SystemClock.uptimeMillis() - frameStart;
                    long wait = FRAME_INTERVAL_MS - elapsed;
                    if (wait > 0) sleepQuietly(wait);
                }
            } catch (Throwable t) {
                persistNativeException("FRAME_EXCEPTION", t);
            } finally {
                try { if (retriever != null) retriever.release(); } catch (Throwable ignored) {}
                try { if (afd != null) afd.close(); } catch (Throwable ignored) {}
                recordStep("FRAME_THREAD_EXIT");
            }
        }

        /**
         * Opens the video source. Returns {retriever, afd, activePath, activeUri, durationMs}.
         * If explicit path/uri are null, reads from prefs. Falls back to RAW test wallpaper.
         */
        private Object[] openSource(String explicitPath, String explicitUri) throws Throwable {
            SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
            String path = explicitPath != null ? explicitPath : prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
            String uri = explicitUri != null ? explicitUri : prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);

            MediaMetadataRetriever r = new MediaMetadataRetriever();
            AssetFileDescriptor afdOut = null;
            String activePath = null;
            String activeUri = null;
            boolean set = false;

            if (path != null) {
                File f = new File(path);
                if (f.exists() && f.canRead() && f.length() > 0) {
                    r.setDataSource(f.getAbsolutePath());
                    recordStep("FRAME_SOURCE_FILE len=" + f.length());
                    activePath = path;
                    set = true;
                }
            }
            if (!set && uri != null && !uri.isEmpty()) {
                try {
                    r.setDataSource(AetherXLiveWallpaperService.this, Uri.parse(uri));
                    recordStep("FRAME_SOURCE_URI " + uri);
                    activeUri = uri;
                    set = true;
                } catch (Throwable t) {
                    persistNativeException("FRAME_URI_OPEN_FAIL", t);
                }
            }
            if (!set) {
                afdOut = getResources().openRawResourceFd(R.raw.testwallpaper);
                if (afdOut == null) {
                    recordStep("FRAME_RAW_AFD_NULL");
                    try { r.release(); } catch (Throwable ignored) {}
                    return new Object[]{null, null, null, null, 8000L};
                }
                r.setDataSource(afdOut.getFileDescriptor(), afdOut.getStartOffset(), afdOut.getLength());
                recordStep("FRAME_SOURCE_RAW");
            }

            long dur = parseLongSafe(
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), 8000L);
            if (dur < 1000L) dur = 8000L;
            recordStep("FRAME_RETRIEVER_READY durationMs=" + dur);
            return new Object[]{r, afdOut, activePath, activeUri, dur};
        }

        private boolean equalsNullable(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }


        private boolean drawFrame(SurfaceHolder holder, Bitmap frame) {
            Canvas canvas = null;
            boolean posted = false;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return false;
                canvas.drawColor(Color.BLACK);
                RectF dst = coverRect(
                    frame.getWidth(),
                    frame.getHeight(),
                    canvas.getWidth(),
                    canvas.getHeight()
                );
                canvas.drawBitmap(frame, null, dst, null);
                holder.unlockCanvasAndPost(canvas);
                posted = true;
                return true;
            } catch (Throwable t) {
                persistNativeException("FRAME_DRAW_FAIL", t);
                return false;
            } finally {
                if (canvas != null && !posted) {
                    try { holder.unlockCanvasAndPost(canvas); } catch (Throwable ignored) {}
                }
            }
        }

        private RectF coverRect(int bw, int bh, int cw, int ch) {
            if (bw <= 0 || bh <= 0 || cw <= 0 || ch <= 0) return new RectF(0, 0, cw, ch);
            float bitmapAspect = (float) bw / (float) bh;
            float canvasAspect = (float) cw / (float) ch;
            if (bitmapAspect > canvasAspect) {
                float h = ch;
                float w = h * bitmapAspect;
                float left = (cw - w) / 2f;
                return new RectF(left, 0f, left + w, h);
            }
            float w = cw;
            float h = w / bitmapAspect;
            float top = (ch - h) / 2f;
            return new RectF(0f, top, w, top + h);
        }

        private long parseLongSafe(String value, long fallback) {
            try {
                if (value == null || value.trim().isEmpty()) return fallback;
                return Long.parseLong(value.trim());
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private void sleepQuietly(long ms) {
            try {
                Thread.sleep(Math.max(1L, ms));
            } catch (InterruptedException ignored) {
                // stopRequested is checked by the loop.
            }
        }
    }
}
