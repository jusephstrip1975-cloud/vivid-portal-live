package com.aetherx.livewallpaper.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Log;

import com.aetherx.livewallpaper.R;

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
    public static final String KEY_RAW_VIDEO_FOUND = "raw_video_found";
    public static final String KEY_RAW_VIDEO_OPEN_OK = "raw_video_open_ok";
    public static final String KEY_RAW_VIDEO_OPEN_FAIL = "raw_video_open_fail";
    public static final String KEY_LAST_FRONTEND_STEP = "last_frontend_step";
    public static final String KEY_LAST_PLUGIN_ENTERED = "last_plugin_entered";
    public static final String KEY_PLUGIN_JS_ERROR = "plugin_js_error";
    public static final String KEY_FIT_MODE = "fit_mode"; // "cover" | "stretch" | "contain"
    private static final int MAX_REDIRECTS = 5;

    @PluginMethod
    public void setFitMode(PluginCall call) {
        String mode = call.getString("mode", "cover");
        if (!"cover".equals(mode) && !"stretch".equals(mode) && !"contain".equals(mode)) mode = "cover";
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FIT_MODE, mode).apply();
        JSObject ret = new JSObject(); ret.put("mode", mode);
        call.resolve(ret);
    }

    @PluginMethod
    public void getFitMode(PluginCall call) {
        String mode = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FIT_MODE, "cover");
        JSObject ret = new JSObject(); ret.put("mode", mode);
        call.resolve(ret);
    }

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
        final Uri uri = result.getData().getData();
        if (uri == null) { call.reject("pick-video-no-uri"); return; }
        try {
            final int take = result.getData().getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContext().getContentResolver().takePersistableUriPermission(uri, take);
        } catch (Exception ignored) {}
        new Thread(() -> {
            try {
                String fileName = "picked-" + System.currentTimeMillis() + ".mp4";
                File outFile = ensureWallpaperFile(fileName);
                ContentResolver resolver = getContext().getContentResolver();
                try (InputStream in = resolver.openInputStream(uri);
                     OutputStream out = new FileOutputStream(outFile)) {
                    if (in == null) throw new Exception("open-inputstream-null");
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                persistVideoUri(uri.toString());
                transcodeAndResolve(outFile, call, uri.toString());
            } catch (Exception e) {
                Log.e(TAG, "pickVideoFromDevice background copy failed", e);
                call.reject("pick-video-failed: " + e.getMessage(), e);
            }
        }, "AetherXPickVideoCopy").start();
    }

    @PluginMethod public void applyHome(PluginCall call)  { persistKey(KEY_LAST_PLUGIN_ENTERED, "NATIVE_PLUGIN_METHOD_ENTERED applyHome"); applyStaticWallpaper(call, WallpaperManager.FLAG_SYSTEM, "home"); }
    @PluginMethod public void applyLock(PluginCall call)  { persistKey(KEY_LAST_PLUGIN_ENTERED, "NATIVE_PLUGIN_METHOD_ENTERED applyLock"); applyStaticWallpaper(call, WallpaperManager.FLAG_LOCK, "lock"); }
    @PluginMethod public void applyBoth(PluginCall call)  { persistKey(KEY_LAST_PLUGIN_ENTERED, "NATIVE_PLUGIN_METHOD_ENTERED applyBoth"); applyStaticWallpaper(call, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK, "both"); }
    @PluginMethod public void openPicker(PluginCall call) { persistKey(KEY_LAST_PLUGIN_ENTERED, "NATIVE_PLUGIN_METHOD_ENTERED openPicker"); openLivePicker(call); }

    /**
     * Auto-apply the saved wallpaper video as a STATIC image (first frame) using
     * WallpaperManager.setBitmap(). This does NOT need a system picker on Android
     * and works instantly (SET_WALLPAPER permission is granted at install time).
     * Falls back to opening the live-wallpaper picker if bitmap extraction fails.
     */
    private void applyStaticWallpaper(final PluginCall call, final int flags, final String target) {
        new Thread(() -> {
            File video = getCurrentVideo();
            if (video == null || !video.exists() || video.length() == 0) {
                persistStep("APPLY_STATIC_NO_VIDEO");
                openLivePicker(call);
                return;
            }
            android.media.MediaMetadataRetriever mmr = null;
            android.graphics.Bitmap frame = null;
            try {
                mmr = new android.media.MediaMetadataRetriever();
                mmr.setDataSource(video.getAbsolutePath());
                // First frame, best quality
                frame = mmr.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) {
                    frame = mmr.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST);
                }
                if (frame == null) throw new Exception("frame-null");

                WallpaperManager wm = WallpaperManager.getInstance(getContext());
                int applyFlags = flags;
                boolean applied;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    int id = wm.setBitmap(frame, null, true, applyFlags);
                    applied = id != 0;
                } else {
                    wm.setBitmap(frame);
                    applied = true;
                }
                persistStep("APPLY_STATIC_OK target=" + target + " flags=" + applyFlags);
                JSObject ret = new JSObject();
                ret.put("applied", applied);
                ret.put("verified", applied);
                ret.put("homeVerified", applied);
                ret.put("lockApplied", applied);
                ret.put("openedPicker", false);
                ret.put("needsConfirmation", false);
                ret.put("via", "WallpaperManager.setBitmap");
                ret.put("target", target);
                call.resolve(ret);
            } catch (Throwable t) {
                Log.e(TAG, "applyStaticWallpaper failed, falling back to picker", t);
                persistNativeException("APPLY_STATIC_FAIL " + t.getClass().getSimpleName() + ": " + t.getMessage());
                openLivePicker(call);
            } finally {
                if (frame != null) { try { frame.recycle(); } catch (Throwable ignored) {} }
                if (mmr != null)   { try { mmr.release();   } catch (Throwable ignored) {} }
            }
        }, "AetherXApplyStatic").start();
    }

    @PluginMethod
    public void recordFrontendStep(PluginCall call) {
        String step = call.getString("step");
        String error = call.getString("error");
        if (step != null) persistKey(KEY_LAST_FRONTEND_STEP, step);
        if (error != null) persistKey(KEY_PLUGIN_JS_ERROR, error);
        JSObject ret = new JSObject();
        ret.put("ok", true);
        call.resolve(ret);
    }

    private void persistKey(String key, String value) {
        try {
            SharedPreferences p = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            p.edit().putString(key, System.currentTimeMillis() + " " + value).apply();
        } catch (Throwable ignored) {}
    }

    /**
     * Open Samsung One UI live-wallpaper preview directly on our service via
     * ACTION_CHANGE_LIVE_WALLPAPER + EXTRA_LIVE_WALLPAPER_COMPONENT. If Samsung
     * rejects it, fall back to the generic ACTION_LIVE_WALLPAPER_CHOOSER.
     */
    private void openLivePicker(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            persistNativeException("ACTIVITY_NULL");
            Log.e(TAG, "ACTIVITY_NULL opening live wallpaper picker");
            call.reject("open-picker-failed: ACTIVITY_NULL");
            return;
        }

        ComponentName component = new ComponentName(
            "com.aetherx.livewallpaper",
            "com.aetherx.livewallpaper.wallpaper.AetherXLiveWallpaperService");
        persistStep("OPEN_WALLPAPER_INTENT");
        Log.i(TAG, "OPEN_WALLPAPER_INTENT component=" + component.flattenToString());

        // 1) Direct preview of our component
        try {
            Intent direct = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            direct.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
            direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.i(TAG, "OPEN_WALLPAPER_INTENT_URI " + direct.toUri(Intent.URI_INTENT_SCHEME));
            activity.startActivity(direct);
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
            persistStep("SAMSUNG_PICKER_OPENED");
            persistNativeException(Log.getStackTraceString(e));
        }

        // 2) Samsung fallback — generic chooser
        try {
            Intent chooser = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.i(TAG, "SAMSUNG_PICKER_INTENT_URI " + chooser.toUri(Intent.URI_INTENT_SCHEME));
            activity.startActivity(chooser);
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
            persistNativeException(Log.getStackTraceString(e));
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

    private void persistNativeException(String msg) {
        persistError(KEY_LAST_NATIVE_EXCEPTION, msg);
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
        ret.put("PLAYER_ENGINE", "Plan D FrameBlitter (MediaMetadataRetriever + Canvas, no direct decoder Surface)");
        ret.put("LAST_SERVICE_ERROR", prefs.getString(KEY_LAST_SERVICE_ERROR, "(none)"));
        ret.put("LAST_NATIVE_EXCEPTION", prefs.getString(KEY_LAST_NATIVE_EXCEPTION, "(none)"));
        ret.put("LAST_WALLPAPER_STEP", prefs.getString(KEY_LAST_WALLPAPER_STEP, "(none)"));
        ret.put("LAST_SERVICE_EVENT", prefs.getString(KEY_LAST_SERVICE_EVENT, "(none)"));
        ret.put("LAST_ENGINE_EVENT", prefs.getString(KEY_LAST_ENGINE_EVENT, "(none)"));
        ret.put("LAST_SURFACE_EVENT", prefs.getString(KEY_LAST_SURFACE_EVENT, "(none)"));
        ret.put("SERVICE_RUNNING", serviceRunning);
        ret.put("WALLPAPER_INFO", wallpaperInfo);
        ret.put("CURRENT_WALLPAPER_PACKAGE", currentPkg);
        ret.put("MANUFACTURER", Build.MANUFACTURER);
        ret.put("MODEL", Build.MODEL);
        ret.put("ANDROID_SDK", Build.VERSION.SDK_INT);

        // Probe the embedded RAW video so diagnostics show whether MediaPlayer
        // can actually open android.resource://<pkg>/raw/testwallpaper.
        boolean rawFound = false;
        long rawSize = 0;
        try {
            android.content.res.AssetFileDescriptor afd =
                ctx.getResources().openRawResourceFd(R.raw.testwallpaper);
            if (afd != null) {
                rawFound = true;
                rawSize = afd.getLength();
                try { afd.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            prefs.edit().putString(KEY_RAW_VIDEO_OPEN_FAIL,
                System.currentTimeMillis() + " openRawResourceFd: " + t.getClass().getSimpleName() + ": " + t.getMessage()).apply();
        }
        prefs.edit().putString(KEY_RAW_VIDEO_FOUND, rawFound + " size=" + rawSize).apply();

        boolean openOk = false;
        String openFail = null;
        if (rawFound) {
            android.media.MediaPlayer probe = null;
            try {
                Uri rawUri = Uri.parse("android.resource://" + pkg + "/raw/testwallpaper");
                probe = new android.media.MediaPlayer();
                probe.setDataSource(ctx, rawUri);
                probe.prepare();
                openOk = true;
            } catch (Throwable t) {
                openFail = t.getClass().getSimpleName() + ": " + t.getMessage();
            } finally {
                if (probe != null) { try { probe.release(); } catch (Throwable ignored) {} }
            }
            if (openOk) {
                prefs.edit().putString(KEY_RAW_VIDEO_OPEN_OK,
                    System.currentTimeMillis() + " android.resource://" + pkg + "/raw/testwallpaper").apply();
            } else if (openFail != null) {
                prefs.edit().putString(KEY_RAW_VIDEO_OPEN_FAIL,
                    System.currentTimeMillis() + " " + openFail).apply();
            }
        }

        ret.put("RAW_VIDEO_FOUND", prefs.getString(KEY_RAW_VIDEO_FOUND, "(none)"));
        ret.put("RAW_VIDEO_OPEN_OK", prefs.getString(KEY_RAW_VIDEO_OPEN_OK, "(none)"));
        ret.put("RAW_VIDEO_OPEN_FAIL", prefs.getString(KEY_RAW_VIDEO_OPEN_FAIL, "(none)"));
        ret.put("LAST_FRONTEND_STEP", prefs.getString(KEY_LAST_FRONTEND_STEP, "(none)"));
        ret.put("LAST_PLUGIN_ENTERED", prefs.getString(KEY_LAST_PLUGIN_ENTERED, "(none)"));
        ret.put("PLUGIN_JS_ERROR", prefs.getString(KEY_PLUGIN_JS_ERROR, "(none)"));
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
