package com.aetherx.livewallpaper.wallpaper;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;

/** Inspects an MP4 to extract real codec / fps / resolution / bitrate / hasAudio. */
public final class WallpaperProbe {

    private static final String TAG = "AetherXLiveWP";

    public static final int TARGET_WIDTH = 1080;
    public static final int TARGET_HEIGHT = 1920;
    public static final int TARGET_FPS = 30;
    public static final String TARGET_CODEC = "video/avc";

    public final String codec;
    public final int width;
    public final int height;
    public final double fps;
    public final long bitrate;
    public final boolean hasAudio;

    private WallpaperProbe(String codec, int width, int height, double fps, long bitrate, boolean hasAudio) {
        this.codec = codec;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bitrate = bitrate;
        this.hasAudio = hasAudio;
    }

    public boolean isSamsungSafe() {
        if (!TARGET_CODEC.equalsIgnoreCase(codec)) return false;
        if (width != TARGET_WIDTH || height != TARGET_HEIGHT) return false;
        if (fps < 28.0 || fps > 32.0) return false;
        if (hasAudio) return false;
        return true;
    }

    public static WallpaperProbe of(File file) {
        if (file == null || !file.exists()) {
            return new WallpaperProbe(null, 0, 0, 0, 0, false);
        }
        MediaExtractor ex = new MediaExtractor();
        String codec = null;
        int width = 0, height = 0;
        double fps = 0;
        long bitrate = 0;
        boolean hasAudio = false;
        try {
            ex.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/") && codec == null) {
                    codec = mime;
                    if (f.containsKey(MediaFormat.KEY_WIDTH)) width = f.getInteger(MediaFormat.KEY_WIDTH);
                    if (f.containsKey(MediaFormat.KEY_HEIGHT)) height = f.getInteger(MediaFormat.KEY_HEIGHT);
                    if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        try { fps = f.getInteger(MediaFormat.KEY_FRAME_RATE); }
                        catch (Throwable t) {
                            try { fps = f.getFloat(MediaFormat.KEY_FRAME_RATE); } catch (Throwable ignored) {}
                        }
                    }
                    if (f.containsKey(MediaFormat.KEY_BIT_RATE)) {
                        try { bitrate = f.getInteger(MediaFormat.KEY_BIT_RATE); } catch (Throwable ignored) {}
                    }
                } else if (mime.startsWith("audio/")) {
                    hasAudio = true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "WallpaperProbe failed path=" + file.getAbsolutePath(), t);
        } finally {
            try { ex.release(); } catch (Throwable ignored) {}
        }
        WallpaperProbe r = new WallpaperProbe(codec, width, height, fps, bitrate, hasAudio);
        Log.i(TAG, "PROBE codec=" + codec + " size=" + width + "x" + height
            + " fps=" + fps + " bitrate=" + bitrate + " hasAudio=" + hasAudio
            + " samsungSafe=" + r.isSamsungSafe());
        return r;
    }
}
