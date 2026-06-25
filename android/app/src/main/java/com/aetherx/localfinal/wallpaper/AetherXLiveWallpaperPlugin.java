package com.aetherx.localfinal.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "AetherXLiveWallpaper")
public class AetherXLiveWallpaperPlugin extends Plugin {

    public static final String PREFS = "aetherx_live_wallpaper";
    public static final String KEY_VIDEO_PATH = "video_path";
    private static final int MAX_REDIRECTS = 5;

    @PluginMethod
    public void saveVideoFromUrl(final PluginCall call) {
        final String url = call.getString("url");
        final String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        if (url == null || url.isEmpty()) {
            call.reject("missing-url");
            return;
        }
        new Thread(() -> {
            try {
                File outFile = ensureWallpaperFile(fileName);
                long bytes = downloadFollowingRedirects(url, outFile);
                persistVideoPath(outFile.getAbsolutePath());
                JSObject ret = new JSObject();
                ret.put("path", outFile.getAbsolutePath());
                ret.put("bytes", bytes);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("download-failed: " + e.getMessage(), e);
            }
        }).start();
    }

    @PluginMethod
    public void saveVideo(PluginCall call) {
        String base64 = call.getString("base64");
        String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        if (base64 == null || base64.isEmpty()) {
            call.reject("missing-base64");
            return;
        }
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            File outFile = ensureWallpaperFile(fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(data);
            }
            persistVideoPath(outFile.getAbsolutePath());
            JSObject ret = new JSObject();
            ret.put("path", outFile.getAbsolutePath());
            ret.put("bytes", outFile.length());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("save-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void pickVideoFromDevice(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(call, intent, "onPickVideoResult");
    }

    @ActivityCallback
    private void onPickVideoResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("pick-video-cancelled");
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            call.reject("pick-video-no-uri");
            return;
        }
        try {
            String fileName = "picked-" + System.currentTimeMillis() + ".mp4";
            File outFile = ensureWallpaperFile(fileName);
            ContentResolver resolver = getContext().getContentResolver();
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                long total = 0;
                while (in != null && (n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
                persistVideoPath(outFile.getAbsolutePath());
                JSObject ret = new JSObject();
                ret.put("path", outFile.getAbsolutePath());
                ret.put("bytes", total);
                ret.put("sourceUri", uri.toString());
                call.resolve(ret);
            }
        } catch (Exception e) {
            call.reject("pick-video-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void applyHome(PluginCall call) {
        openLivePicker(call);
    }

    @PluginMethod
    public void applyLock(PluginCall call) {
        openLivePicker(call);
    }

    @PluginMethod
    public void applyBoth(PluginCall call) {
        openLivePicker(call);
    }

    @PluginMethod
    public void openPicker(PluginCall call) {
        openLivePicker(call);
    }

    private void openLivePicker(PluginCall call) {
        try {
            ComponentName comp = new ComponentName(
                getContext().getPackageName(),
                AetherXLiveWallpaperService.class.getName()
            );
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            JSObject ret = new JSObject();
            ret.put("applied", false);
            ret.put("openedPicker", true);
            ret.put("needsConfirmation", true);
            ret.put("opened", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("open-picker-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void checkCompatibility(PluginCall call) {
        WallpaperManager wm = WallpaperManager.getInstance(getContext());
        boolean wallpaperSupported = wm.isWallpaperSupported();
        boolean setAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.N || wm.isSetWallpaperAllowed();
        File current = getCurrentVideo();
        JSObject ret = new JSObject();
        ret.put("canApplyHome", wallpaperSupported && setAllowed);
        ret.put("canApplyLock", wallpaperSupported && setAllowed);
        ret.put("liveWallpaperSupported", true);
        ret.put("wallpaperSupported", wallpaperSupported);
        ret.put("setWallpaperAllowed", setAllowed);
        ret.put("serviceRegistered", true);
        ret.put("hasVideo", current != null && current.exists() && current.length() > 0);
        ret.put("isSamsung", "samsung".equalsIgnoreCase(Build.MANUFACTURER));
        ret.put("manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
        ret.put("sdk", Build.VERSION.SDK_INT);
        ret.put("reason", "ok");
        ret.put("message", "Live wallpaper service registered");
        call.resolve(ret);
    }

    private File ensureWallpaperFile(String fileName) {
        File dir = new File(getContext().getFilesDir(), "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }

    private void persistVideoPath(String absolutePath) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VIDEO_PATH, absolutePath).apply();
    }

    private File getCurrentVideo() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String p = prefs.getString(KEY_VIDEO_PATH, null);
        return p == null ? null : new File(p);
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "wallpaper.mp4";
        String safe = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!safe.toLowerCase().endsWith(".mp4")) safe = safe + ".mp4";
        return safe;
    }

    private long downloadFollowingRedirects(String urlStr, File out) throws Exception {
        int redirects = 0;
        String current = urlStr;
        while (true) {
            URL url = new URL(current);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "AetherX/1.0");
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || ++redirects > MAX_REDIRECTS) throw new Exception("too-many-redirects");
                current = loc;
                continue;
            }
            if (code < 200 || code >= 300) {
                conn.disconnect();
                throw new Exception("http-" + code);
            }
            long total = 0;
            try (InputStream in = conn.getInputStream();
                 OutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    total += n;
                }
            } finally {
                conn.disconnect();
            }
            return total;
        }
    }
}
