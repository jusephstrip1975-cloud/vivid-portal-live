package com.aetherx.livewallpaper.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;

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

    private static final String TAG = "AetherXLiveWP";
    public static final String PREFS = "aetherx_live_wallpaper";
    public static final String KEY_VIDEO_PATH = "video_path";
    public static final String KEY_VIDEO_URI = "video_uri";
    public static final String KEY_LAST_SERVICE_ERROR = "last_service_error";
    public static final String KEY_LAST_NATIVE_EXCEPTION = "last_native_exception";
    public static final String KEY_LAST_WALLPAPER_STEP = "last_wallpaper_step";
    public static final String KEY_LAST_SERVICE_EVENT = "last_service_event";
    public static final String KEY_LAST_ENGINE_EVENT = "last_engine_event";
    public static final String KEY_LAST_SURFACE_EVENT = "last_surface_event";
    public static final String KEY_SERVICE_RUNNING = "service_running";
    private static final int MAX_REDIRECTS = 5;

    @PluginMethod
    public void saveVideoFromUrl(final PluginCall call) {
        final String url = call.getString("url");
        final String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        if (url == null || url.isEmpty()) { call.reject("missing-url"); return; }
        new Thread(() -> {
            try {
                File outFile = ensureWallpaperFile(fileName);
                downloadFollowingRedirects(url, outFile);
                transcodeAndResolve(outFile, call);
            } catch (Exception e) {
                Log.e(TAG, "saveVideoFromUrl failed", e);
                call.reject("download-failed: " + e.getMessage(), e);
            }
        }).start();
    }

    @PluginMethod
    public void saveVideo(PluginCall call) {
        String base64 = call.getString("base64");
        String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        if (base64 == null || base64.isEmpty()) { call.reject("missing-base64"); return; }
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            File outFile = ensureWallpaperFile(fileName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(data); }
            transcodeAndResolve(outFile, call);
        } catch (Exception e) {
            call.reject("save-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void pickVideoFromDevice(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "onPickVideoResult");
    }

    @ActivityCallback
    private void onPickVideoResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("pick-video-cancelled"); return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) { call.reject("pick-video-no-uri"); return; }
        try {
            final int take = result.getData().getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContext().getContentResolver().takePersistableUriPermission(uri, take);
        } catch (Exception ignored) {}
        try {
            String fileName = "picked-" + System.currentTimeMillis() + ".mp4";
            File outFile = ensureWallpaperFile(fileName);
            ContentResolver resolver = getContext().getContentResolver();
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                while (in != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            persistVideoUri(uri.toString());
            transcodeAndResolve(outFile, call, uri.toString());
        } catch (Exception e) {
            call.reject("pick-video-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod public void applyHome(PluginCall call)  { openLivePicker(call); }
    @PluginMethod public void applyLock(PluginCall call)  { openLivePicker(call); }
    @PluginMethod public void applyBoth(PluginCall call)  { openLivePicker(call); }
    @PluginMethod public void openPicker(PluginCall call) { openLivePicker(call); }

    /**
     * Open Samsung One UI live-wallpaper preview directly on our service via
     * ACTION_CHANGE_LIVE_WALLPAPER + EXTRA_LIVE_WALLPAPER_COMPONENT. If Samsung
     * rejects it, fall back to the generic ACTION_LIVE_WALLPAPER_CHOOSER.
     */
    private void openLivePicker(PluginCall call) {
        Activity activity = getActivity();
        Context ctx = getContext();
        android.content.ComponentName component = new android.content.ComponentName(
            ctx, AetherXLiveWallpaperService.class);
        persistStep("OPEN_WALLPAPER_INTENT component=" + component.flattenToShortString());
        Log.i(TAG, "OPEN_WALLPAPER_INTENT " + component.flattenToShortString());

        // 1) Direct preview of our component
        try {
            Intent direct = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            direct.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
            direct.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activity != null) activity.startActivity(direct);
            else ctx.startActivity(direct);
            persistStep("LIVE_COMPONENT_SENT");
            Log.i(TAG, "LIVE_COMPONENT_SENT");
            JSObject ret = new JSObject();
            ret.put("applied", false);
            ret.put("openedPicker", true);
            ret.put("needsConfirmation", true);
            ret.put("opened", true);
            ret.put("via", "ACTION_CHANGE_LIVE_WALLPAPER");
            call.resolve(ret);
            return;
        } catch (Exception e) {
            Log.w(TAG, "ACTION_CHANGE_LIVE_WALLPAPER rejected, falling back", e);
            persistError(KEY_LAST_NATIVE_EXCEPTION, "CHANGE_LIVE_WALLPAPER " + e);
        }

        // 2) Samsung fallback — generic chooser
        try {
            Intent chooser = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activity != null) activity.startActivity(chooser);
            else ctx.startActivity(chooser);
            persistStep("SAMSUNG_PICKER_OPENED");
            Log.i(TAG, "SAMSUNG_PICKER_OPENED");
            JSObject ret = new JSObject();
            ret.put("applied", false);
            ret.put("openedPicker", true);
            ret.put("needsConfirmation", true);
            ret.put("opened", true);
            ret.put("via", "ACTION_LIVE_WALLPAPER_CHOOSER");
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "open-picker-failed", e);
            persistError(KEY_LAST_NATIVE_EXCEPTION, "open-picker-failed " + e);
            call.reject("open-picker-failed: " + e.getMessage(), e);
        }
    }

    private void persistStep(String step) {
        try {
            SharedPreferences p = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putString(KEY_LAST_WALLPAPER_STEP, System.currentTimeMillis() + " " + step).apply();
        } catch (Throwable ignored) {}
    }

    private void persistError(String key, String msg) {
        try {
            SharedPreferences p = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putString(key, System.currentTimeMillis() + " " + msg).apply();
        } catch (Throwable ignored) {}
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_VIDEO_PATH, null);
        String uri = prefs.getString(KEY_VIDEO_URI, null);
        File f = path == null ? null : new File(path);
        boolean exists = f != null && f.exists();
        long size = f != null && f.exists() ? f.length() : 0;
        boolean canRead = f != null && f.canRead();
        boolean fdOk = false;
        String fdErr = null;
        if (f != null && f.exists()) {
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) {
                fdOk = pfd != null;
            } catch (Exception e) { fdErr = e.getMessage(); }
        }
        JSObject ret = new JSObject();
        ret.put("savedPath", path);
        ret.put("savedUri", uri);
        ret.put("exists", exists);
        ret.put("size", size);
        ret.put("canRead", canRead);
        ret.put("fdOk", fdOk);
        if (fdErr != null) ret.put("fdError", fdErr);
        call.resolve(ret);
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

    @PluginMethod
    public void getDiagnostics(PluginCall call) {
        JSObject ret = new JSObject();
        Context ctx = getContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        String pkg = ctx.getPackageName();
        String appVersion = "?";
        long buildId = 0;
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            appVersion = pi.versionName + " (" + pi.versionCode + ")";
            buildId = pi.lastUpdateTime;
        } catch (Exception ignored) {}

        String path = prefs.getString(KEY_VIDEO_PATH, null);
        File f = path == null ? null : new File(path);
        boolean exists = f != null && f.exists();
        long size = exists ? f.length() : 0;
        boolean canRead = f != null && f.canRead();

        WallpaperManager wm = WallpaperManager.getInstance(ctx);
        android.app.WallpaperInfo info = wm.getWallpaperInfo();
        String currentPkg = info == null ? "(static wallpaper)" : info.getPackageName();
        String wallpaperInfo = info == null ? "null" : (info.getPackageName() + "/" + info.getServiceName());
        boolean serviceRunning = info != null && pkg.equals(info.getPackageName());

        ret.put("APP_VERSION", appVersion);
        ret.put("BUILD_ID", String.valueOf(buildId));
        ret.put("PACKAGE_NAME", pkg);
        ret.put("SERVICE_CLASS", AetherXLiveWallpaperService.class.getName());
        ret.put("VIDEO_PATH", path == null ? "(none)" : path);
        ret.put("VIDEO_EXISTS", exists);
        ret.put("VIDEO_SIZE", size);
        ret.put("VIDEO_CAN_READ", canRead);
        ret.put("PLAYER_ENGINE", "MediaPlayer (RAW res/raw/testwallpaper.mp4)");
        ret.put("LAST_SERVICE_ERROR", prefs.getString(KEY_LAST_SERVICE_ERROR, "(none)"));
        ret.put("LAST_NATIVE_EXCEPTION", prefs.getString(KEY_LAST_NATIVE_EXCEPTION, "(none)"));
        ret.put("LAST_WALLPAPER_STEP", prefs.getString(KEY_LAST_WALLPAPER_STEP, "(none)"));
        ret.put("SERVICE_RUNNING", serviceRunning);
        ret.put("WALLPAPER_INFO", wallpaperInfo);
        ret.put("CURRENT_WALLPAPER_PACKAGE", currentPkg);
        ret.put("MANUFACTURER", Build.MANUFACTURER);
        ret.put("MODEL", Build.MODEL);
        ret.put("ANDROID_SDK", Build.VERSION.SDK_INT);
        call.resolve(ret);
    }

    private File ensureWallpaperFile(String fileName) {
        File dir = new File(getContext().getFilesDir(), "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }

    private void transcodeAndResolve(final File input, final PluginCall call) {
        transcodeAndResolve(input, call, null);
    }

    private void transcodeAndResolve(final File input, final PluginCall call, final String sourceUri) {
        WallpaperVideoTranscoder.transcode(getContext(), input, new WallpaperVideoTranscoder.Callback() {
            @Override public void onSuccess(File output) {
                persistVideoPath(output.getAbsolutePath());
                JSObject ret = new JSObject();
                ret.put("path", output.getAbsolutePath());
                ret.put("bytes", output.length());
                ret.put("transcoded", true);
                if (sourceUri != null) ret.put("sourceUri", sourceUri);
                call.resolve(ret);
            }
            @Override public void onFailure(Exception error) {
                call.reject("transcode-failed: " + (error.getMessage() == null ? "unknown" : error.getMessage()), error);
            }
        });
    }

    private void persistVideoPath(String absolutePath) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VIDEO_PATH, absolutePath).commit();
    }

    private void persistVideoUri(String uri) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VIDEO_URI, uri).commit();
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
            if (code < 200 || code >= 300) { conn.disconnect(); throw new Exception("http-" + code); }
            long total = 0;
            try (InputStream in = conn.getInputStream();
                 OutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) { fos.write(buf, 0, n); total += n; }
            } finally { conn.disconnect(); }
            return total;
        }
    }
}
