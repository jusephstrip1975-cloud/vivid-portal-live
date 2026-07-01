package com.aetherx.livewallpaper.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.aetherx.livewallpaper.R;

import java.io.File;
import java.io.FileDescriptor;
import java.nio.ByteBuffer;

/**
 * Plan C: manual MediaExtractor + MediaCodec decoding straight into the
 * wallpaper Surface. No MediaPlayer, no ExoPlayer, no Player abstractions —
 * avoids every "keep screen on" / player-lifecycle pitfall that broke Plans
 * A and B on Samsung One UI.
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
        private DecoderThread decoder;
        private volatile boolean visible = false;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setOffsetNotificationsEnabled(false);
            setTouchEventsEnabled(false);
            recordStep("ENGINE_CREATED");
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_ENGINE_EVENT, "engineOnCreate");
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
            if (valid) startDecoder(s);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;
            recordKey(AetherXLiveWallpaperPlugin.KEY_LAST_SURFACE_EVENT, "surfaceChanged " + width + "x" + height);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            super.onVisibilityChanged(v);
            visible = v;
            if (decoder != null) decoder.setPaused(!v);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            recordStep("SURFACE_DESTROYED");
            stopDecoder();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            recordStep("ENGINE_DESTROYED");
            stopDecoder();
            super.onDestroy();
        }

        private void startDecoder(Surface surface) {
            stopDecoder();
            clearNativeFailureState();
            clearSurface(currentHolder);
            decoder = new DecoderThread(surface);
            decoder.start();
        }

        private void stopDecoder() {
            if (decoder != null) {
                decoder.requestStop();
                decoder = null;
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
     * Runs a MediaExtractor + MediaCodec loop rendering directly to the
     * wallpaper Surface. Loops indefinitely by seeking to 0 on EOS.
     */
    private class DecoderThread extends Thread {
        private final Surface surface;
        private volatile boolean stopRequested = false;
        private volatile boolean paused = false;

        DecoderThread(Surface surface) {
            super("AetherXCodecThread");
            this.surface = surface;
        }

        void requestStop() { stopRequested = true; }
        void setPaused(boolean p) { paused = p; }

        @Override
        public void run() {
            MediaExtractor extractor = null;
            MediaCodec codec = null;
            AssetFileDescriptor afd = null;
            ParcelFileDescriptor pfd = null;

            try {
                extractor = new MediaExtractor();

                // Resolve source: selected file -> selected content URI -> RAW fallback.
                SharedPreferences prefs = getSharedPreferences(AetherXLiveWallpaperPlugin.PREFS, Context.MODE_PRIVATE);
                String selectedPath = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_PATH, null);
                String selectedUri = prefs.getString(AetherXLiveWallpaperPlugin.KEY_VIDEO_URI, null);

                boolean sourceSet = false;
                if (selectedPath != null) {
                    File f = new File(selectedPath);
                    if (f.exists() && f.canRead() && f.length() > 0) {
                        extractor.setDataSource(f.getAbsolutePath());
                        recordStep("CODEC_SOURCE_FILE len=" + f.length());
                        sourceSet = true;
                    }
                }
                if (!sourceSet && selectedUri != null && !selectedUri.isEmpty()) {
                    try {
                        pfd = getContentResolver().openFileDescriptor(Uri.parse(selectedUri), "r");
                        if (pfd != null) {
                            extractor.setDataSource(pfd.getFileDescriptor());
                            recordStep("CODEC_SOURCE_URI " + selectedUri);
                            sourceSet = true;
                        }
                    } catch (Throwable t) {
                        persistNativeException("CODEC_URI_OPEN_FAIL", t);
                    }
                }
                if (!sourceSet) {
                    afd = getResources().openRawResourceFd(R.raw.testwallpaper);
                    if (afd == null) {
                        recordStep("CODEC_RAW_AFD_NULL");
                        return;
                    }
                    extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    recordStep("CODEC_SOURCE_RAW");
                }

                // Pick the first video track.
                int videoTrack = -1;
                MediaFormat format = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat f = extractor.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        videoTrack = i;
                        format = f;
                        break;
                    }
                }
                if (videoTrack < 0 || format == null) {
                    recordStep("CODEC_NO_VIDEO_TRACK");
                    return;
                }
                extractor.selectTrack(videoTrack);

                String mime = format.getString(MediaFormat.KEY_MIME);
                recordStep("CODEC_TRACK_SELECTED mime=" + mime);

                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, surface, null, 0);
                codec.start();
                recordStep("CODEC_STARTED");

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputEOS = false;
                long startMs = System.currentTimeMillis();
                long firstPtsUs = -1;
                boolean firstFrameRendered = false;

                while (!stopRequested) {
                    if (paused) {
                        Thread.sleep(50);
                        continue;
                    }

                    // Feed input.
                    if (!inputEOS) {
                        int inIndex = codec.dequeueInputBuffer(10_000);
                        if (inIndex >= 0) {
                            ByteBuffer buf = codec.getInputBuffer(inIndex);
                            int sampleSize = (buf == null) ? -1 : extractor.readSampleData(buf, 0);
                            if (sampleSize < 0) {
                                // EOF — loop by seeking back to 0.
                                codec.queueInputBuffer(inIndex, 0, 0, 0, 0);
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                                startMs = System.currentTimeMillis();
                                firstPtsUs = -1;
                            } else {
                                long ptsUs = extractor.getSampleTime();
                                codec.queueInputBuffer(inIndex, 0, sampleSize, ptsUs, 0);
                                extractor.advance();
                            }
                        }
                    }

                    // Drain output.
                    int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                    if (outIndex >= 0) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            codec.releaseOutputBuffer(outIndex, false);
                            // shouldn't happen with loop, but be safe
                            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            startMs = System.currentTimeMillis();
                            firstPtsUs = -1;
                            continue;
                        }

                        // Basic PTS pacing.
                        if (firstPtsUs < 0) firstPtsUs = info.presentationTimeUs;
                        long targetMs = (info.presentationTimeUs - firstPtsUs) / 1000L;
                        long nowElapsed = System.currentTimeMillis() - startMs;
                        long wait = targetMs - nowElapsed;
                        if (wait > 0 && wait < 500) {
                            try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
                        }
                        codec.releaseOutputBuffer(outIndex, true);
                        if (!firstFrameRendered) {
                            firstFrameRendered = true;
                            recordStep("CODEC_FIRST_FRAME");
                        }
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        recordStep("CODEC_OUTPUT_FORMAT_CHANGED");
                    }
                }
            } catch (Throwable t) {
                persistNativeException("CODEC_EXCEPTION", t);
            } finally {
                try { if (codec != null) { codec.stop(); codec.release(); } } catch (Throwable ignored) {}
                try { if (extractor != null) extractor.release(); } catch (Throwable ignored) {}
                try { if (afd != null) afd.close(); } catch (Throwable ignored) {}
                try { if (pfd != null) pfd.close(); } catch (Throwable ignored) {}
                recordStep("CODEC_THREAD_EXIT");
            }
        }
    }
}
