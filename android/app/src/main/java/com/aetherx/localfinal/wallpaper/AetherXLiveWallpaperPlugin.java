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
    public static final String KEY_ORIGINAL_PATH = "original_path";
    public static final String KEY_VIDEO_URI = "video_uri";
    public static final String KEY_VIDEO_VERSION = "video_version";
    private static final int MAX_REDIRECTS = 5;

    @PluginMethod
    public void saveVideoFromUrl(final PluginCall call) {
        final String url = call.getString("url");
        final String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        Log.i(TAG, "saveVideoFromUrl url=" + url + " fileName=" + fileName);
        if (url == null || url.isEmpty()) {
            call.reject("missing-url");
            return;
        }
        new Thread(() -> {
            try {
                File outFile = ensureWallpaperFile(fileName);
                Log.i(TAG, "saveVideoFromUrl downloading to=" + outFile.getAbsolutePath());
                long bytes = downloadFollowingRedirects(url, outFile);
                Log.i(TAG, "saveVideoFromUrl downloaded bytes=" + bytes
                    + " exists=" + outFile.exists() + " size=" + outFile.length()
                    + " canRead=" + outFile.canRead());
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
        Log.i(TAG, "saveVideo base64Len=" + (base64 == null ? 0 : base64.length()) + " fileName=" + fileName);
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
            Log.i(TAG, "saveVideo wrote bytes=" + outFile.length() + " path=" + outFile.getAbsolutePath());
            transcodeAndResolve(outFile, call);
        } catch (Exception e) {
            Log.e(TAG, "saveVideo failed", e);
            call.reject("save-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void pickVideoFromDevice(PluginCall call) {
        Log.i(TAG, "pickVideoFromDevice opening ACTION_OPEN_DOCUMENT");
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
            Log.w(TAG, "onPickVideoResult cancelled or no data");
            call.reject("pick-video-cancelled");
            return;
        }
        Uri uri = result.getData().getData();
        Log.i(TAG, "onPickVideoResult selectedVideoUri=" + uri);
        if (uri == null) {
            call.reject("pick-video-no-uri");
            return;
        }
        // Persist URI permission so the WallpaperService can read it later
        try {
            final int take = result.getData().getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContext().getContentResolver().takePersistableUriPermission(uri, take);
            Log.i(TAG, "takePersistableUriPermission OK for " + uri);
        } catch (Exception e) {
            Log.w(TAG, "takePersistableUriPermission failed (will still copy to filesDir): " + e.getMessage());
        }
        try {
            String fileName = "picked-" + System.currentTimeMillis() + ".mp4";
            File outFile = ensureWallpaperFile(fileName);
            ContentResolver resolver = getContext().getContentResolver();
            long total = 0;
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int n;
                while (in != null && (n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
            }
            Log.i(TAG, "Copied picked video bytes=" + total + " to=" + outFile.getAbsolutePath()
                + " exists=" + outFile.exists() + " canRead=" + outFile.canRead());
            persistVideoUri(uri.toString());
            transcodeAndResolve(outFile, call, uri.toString());
        } catch (Exception e) {
            Log.e(TAG, "pick-video-failed", e);
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
            File current = getCurrentVideo();
            Log.i(TAG, "openLivePicker savedWallpaperVideo=" + (current == null ? "null" : current.getAbsolutePath())
                + " exists=" + (current != null && current.exists())
                + " size=" + (current == null ? -1 : current.length()));
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
            Log.e(TAG, "open-picker-failed", e);
            call.reject("open-picker-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_VIDEO_PATH, null);
        String original = prefs.getString(KEY_ORIGINAL_PATH, null);
        String uri = prefs.getString(KEY_VIDEO_URI, null);
        String lastError = prefs.getString("last_transcode_error", null);
        long version = prefs.getLong(KEY_VIDEO_VERSION, 0L);
        long updatedAt = prefs.getLong("video_updated_at", 0L);
        File f = path == null ? null : new File(path);
        boolean exists = f != null && f.exists();
        long size = f != null && f.exists() ? f.length() : 0;
        boolean canRead = f != null && f.canRead();
        boolean fdOk = false;
        String fdErr = null;
        if (f != null && f.exists()) {
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) {
                fdOk = pfd != null;
            } catch (Exception e) {
                fdErr = e.getMessage();
            }
        }
        Log.i(TAG, "getStatus path=" + path + " uri=" + uri
            + " exists=" + exists + " size=" + size + " canRead=" + canRead
            + " fdOk=" + fdOk + " fdErr=" + fdErr
            + " version=" + version + " updatedAt=" + updatedAt
            + " originalSource=" + original + " lastTranscodeError=" + lastError);
        JSObject ret = new JSObject();
        ret.put("savedPath", path);
        ret.put("originalSourcePath", original);
        ret.put("savedUri", uri);
        ret.put("exists", exists);
        ret.put("size", size);
        ret.put("canRead", canRead);
        ret.put("fdOk", fdOk);
        ret.put("version", version);
        ret.put("updatedAt", updatedAt);
        ret.put("renderer", "native-wallpaper-service");
        ret.put("playbackSpeed", 1.0);
        ret.put("droppedFrames", "logged-by-renderer");
        if (lastError != null) ret.put("lastTranscodeError", lastError);
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


    private File ensureWallpaperFile(String fileName) {
        File dir = new File(getContext().getFilesDir(), "wallpapers");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, fileName);
    }

    /** Transcode the downloaded MP4 to Samsung-friendly H.264/AAC. Original MP4 is never used as wallpaper. */
    private void transcodeAndResolve(final File input, final PluginCall call) {
        transcodeAndResolve(input, call, null);
    }

    private void transcodeAndResolve(final File input, final PluginCall call, final String sourceUri) {
        Log.i(TAG, "transcodeAndResolve start input=" + input.getAbsolutePath()
            + " inputExists=" + input.exists() + " inputSize=" + input.length());
        // Keep the original only as a private reconversion source. It is never
        // persisted as KEY_VIDEO_PATH and never played by the WallpaperService.
        persistOriginalPath(input.getAbsolutePath());
        WallpaperVideoTranscoder.transcode(getContext(), input, new WallpaperVideoTranscoder.Callback() {
            @Override
            public void onSuccess(File output) {
                Log.i(TAG, "transcodeAndResolve onSuccess output=" + output.getAbsolutePath()
                    + " outputExists=" + output.exists() + " outputSize=" + output.length());
                persistVideoPath(output.getAbsolutePath());
                WallpaperVideoTranscoder.deleteAllConvertedOutputsExcept(getContext(), output.getAbsolutePath());
                JSObject ret = new JSObject();
                ret.put("path", output.getAbsolutePath());
                ret.put("bytes", output.length());
                ret.put("transcoded", true);
                if (sourceUri != null) ret.put("sourceUri", sourceUri);
                call.resolve(ret);
            }

            @Override
            public void onFailure(Exception error) {
                Log.e(TAG, "Transcode failed after mandatory primary+aggressive passes; original playback disabled", error);
                SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit()
                    .putString("last_transcode_error", error.getMessage() == null ? "unknown" : error.getMessage())
                    .putLong("video_updated_at", System.currentTimeMillis())
                    .commit();
                call.reject("transcode-failed-no-original-fallback: "
                    + (error.getMessage() == null ? "unknown" : error.getMessage()), error);
            }
        });
    }

    private void persistOriginalPath(String absolutePath) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String previousOriginal = prefs.getString(KEY_ORIGINAL_PATH, null);
        String currentVideo = prefs.getString(KEY_VIDEO_PATH, null);
        prefs.edit().putString(KEY_ORIGINAL_PATH, absolutePath).commit();
        Log.i(TAG, "persistOriginalPath=" + absolutePath);
        if (previousOriginal != null
            && !previousOriginal.equals(absolutePath)
            && !previousOriginal.equals(currentVideo)) {
            deleteIfStale(previousOriginal, absolutePath, "previous-original-path");
        }
    }

    private void persistVideoPath(String absolutePath) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String previous = prefs.getString(KEY_VIDEO_PATH, null);
        long version = prefs.getLong(KEY_VIDEO_VERSION, 0L) + 1L;
        prefs.edit()
            .putString(KEY_VIDEO_PATH, absolutePath)
            .remove("last_transcode_error")
            .putLong("video_updated_at", System.currentTimeMillis())
            .putLong(KEY_VIDEO_VERSION, version)
            .commit();
        Log.i(TAG, "persistVideoPath previous=" + previous
            + " new=" + absolutePath
            + " version=" + version
            + " verifyRead=" + prefs.getString(KEY_VIDEO_PATH, null));
        deleteIfStale(previous, absolutePath, "previous-video-path");
        WallpaperVideoTranscoder.deleteAllConvertedOutputsExcept(getContext(), absolutePath);
    }

    private void deleteIfStale(String candidate, String keep, String label) {
        if (candidate == null || candidate.equals(keep)) return;
        try {
            File old = new File(candidate);
            File filesRoot = getContext().getFilesDir();
            String root = filesRoot.getCanonicalPath();
            String target = old.getCanonicalPath();
            if (!target.startsWith(root)) {
                Log.w(TAG, "Not deleting stale " + label + " outside app filesDir: " + target);
                return;
            }
            if (old.exists() && old.delete()) {
                Log.i(TAG, "Deleted stale " + label + "=" + candidate);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not delete stale " + label + ": " + t.getMessage());
        }
    }

    private void persistVideoUri(String uri) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VIDEO_URI, uri).commit();
        Log.i(TAG, "persistVideoUri savedUri=" + uri);
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
            Log.i(TAG, "download HTTP " + code + " <- " + current);
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
