package com.aetherx.localfinal.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
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
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "AetherXLiveWallpaper")
public class AetherXLiveWallpaperPlugin extends Plugin {

    private static final String TAG = "AetherXLiveWP";
    public static final String PREFS = "aetherx_live_wallpaper";
    public static final String KEY_VIDEO_PATH = "video_path";
    public static final String KEY_VIDEO_VERSION = "video_version";

    private static final int MAX_REDIRECTS = 5;
    private static final long MIN_VALID_VIDEO_BYTES = 1024L * 1024L;
    private static final String WALLPAPER_DIR = "wallpapers";
    private static final String CURRENT_MP4 = "current.mp4";
    private static final long MIN_FREE_SPACE_BYTES = 10L * 1024L * 1024L * 1024L; // 10 GB
    private static final String LOW_STORAGE_MESSAGE = "Espacio insuficiente para procesar wallpapers 3D";

    /** Returns null if there is enough space; otherwise the user-facing error message. */
    private String guardStorageOrReject(String stage) {
        try {
            File dir = getWallpaperDir();
            StatFs stat = new StatFs(dir.getAbsolutePath());
            long freeBytes = stat.getAvailableBytes();
            long freeMb = freeBytes / (1024L * 1024L);
            Log.i(TAG, "FREE_SPACE_MB=" + freeMb + " stage=" + stage + " path=" + dir.getAbsolutePath());
            if (freeBytes < MIN_FREE_SPACE_BYTES) {
                Log.w(TAG, "LOW_STORAGE_WARNING freeMb=" + freeMb + " requiredMb="
                    + (MIN_FREE_SPACE_BYTES / (1024L * 1024L)) + " stage=" + stage);
                Log.e(TAG, "DOWNLOAD_ABORTED_LOW_STORAGE stage=" + stage + " freeMb=" + freeMb);
                return LOW_STORAGE_MESSAGE + " (libre " + freeMb + " MB)";
            }
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "storage-check-failed stage=" + stage, t);
            return null; // do not block on probe failure
        }
    }

    /** Removes any file in filesDir/wallpapers/ that is not current.mp4. */
    private void cleanOrphanWallpaperFiles(String stage) {
        File dir = getWallpaperDir();
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                // legacy "converted" or similar — drop entire tree
                File[] inner = entry.listFiles();
                if (inner != null) {
                    for (File f : inner) deleteFileIfExists(f, "ORPHAN_CLEANUP " + stage + " nested");
                }
                boolean removed = entry.delete();
                Log.i(TAG, "ORPHAN_CLEANUP " + stage + " dir=" + entry.getAbsolutePath() + " removed=" + removed);
                continue;
            }
            if (CURRENT_MP4.equals(entry.getName())) continue;
            deleteFileIfExists(entry, "ORPHAN_CLEANUP " + stage);
        }
    }



    @PluginMethod
    public void saveVideoFromUrl(final PluginCall call) {
        final String url = call.getString("url");
        final String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        final String wallpaperId = call.getString("wallpaperId", fileName);
        Log.i(TAG, "saveVideoFromUrl wallpaperId=" + wallpaperId + " url=" + url + " fileName=" + fileName);
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "SAVE_FAILED reason=missing-url wallpaperId=" + wallpaperId);
            call.reject("missing-url");
            return;
        }

        new Thread(() -> {
            File current = getCurrentWallpaperFile();
            try {
                prepareForNewWallpaper(wallpaperId);
                Log.i(TAG, "download-start wallpaperId=" + wallpaperId + " current=" + current.getAbsolutePath());
                long bytes = downloadFollowingRedirects(url, current);
                Log.i(TAG, "download-complete wallpaperId=" + wallpaperId
                    + " bytes=" + bytes
                    + " exists=" + current.exists()
                    + " size=" + current.length()
                    + " absolute=" + current.getAbsolutePath());

                current = commitValidatedCurrentMp4(current, wallpaperId, "download");
                resolveSaved(call, current, false, null, "current-mp4-persistent");
            } catch (Exception e) {
                Log.e(TAG, "SAVE_FAILED wallpaperId=" + wallpaperId
                    + " current=" + current.getAbsolutePath(), e);
                deleteFileIfExists(current, "SAVE_FAILED cleanup-current");
                clearPersistedVideoPath();
                call.reject("save-failed: " + e.getMessage(), e);
            }
        }).start();
    }

    @PluginMethod
    public void saveVideo(PluginCall call) {
        String base64 = call.getString("base64");
        String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        Log.i(TAG, "saveVideo base64Len=" + (base64 == null ? 0 : base64.length()) + " fileName=" + fileName);
        if (base64 == null || base64.isEmpty()) {
            Log.e(TAG, "SAVE_FAILED reason=missing-base64 fileName=" + fileName);
            call.reject("missing-base64");
            return;
        }

        File current = getCurrentWallpaperFile();
        try {
            prepareForNewWallpaper(fileName);
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            try (FileOutputStream fos = new FileOutputStream(current, false)) {
                fos.write(data);
                fos.getFD().sync();
            }
            Log.i(TAG, "saveVideo wrote bytes=" + current.length() + " path=" + current.getAbsolutePath());
            current = commitValidatedCurrentMp4(current, fileName, "base64");
            resolveSaved(call, current, false, null, "current-mp4-persistent");
        } catch (Exception e) {
            Log.e(TAG, "SAVE_FAILED fileName=" + fileName
                + " current=" + current.getAbsolutePath(), e);
            deleteFileIfExists(current, "SAVE_FAILED cleanup-current");
            clearPersistedVideoPath();
            call.reject("save-failed: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void pickVideoFromDevice(PluginCall call) {
        Log.i(TAG, "pickVideoFromDevice opening ACTION_OPEN_DOCUMENT copy-to-internal-current-only=true");
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
            Log.w(TAG, "onPickVideoResult cancelled or no data");
            call.reject("pick-video-cancelled");
            return;
        }

        Uri uri = result.getData().getData();
        Log.i(TAG, "onPickVideoResult selectedVideoUri=" + uri + " note=will-copy-to-filesDir-current-mp4-no-uri-persist");
        if (uri == null) {
            Log.e(TAG, "SAVE_FAILED reason=pick-video-no-uri");
            call.reject("pick-video-no-uri");
            return;
        }

        File current = getCurrentWallpaperFile();
        try {
            prepareForNewWallpaper("picked-video");
            ContentResolver resolver = getContext().getContentResolver();
            long total = 0;
            try (InputStream in = resolver.openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(current, false)) {
                if (in == null) throw new Exception("source-open-failed");
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
                out.getFD().sync();
            }
            Log.i(TAG, "Copied picked video bytes=" + total + " to=" + current.getAbsolutePath()
                + " exists=" + current.exists() + " canRead=" + current.canRead());
            current = commitValidatedCurrentMp4(current, "picked-video", "picked");
            resolveSaved(call, current, false, uri.toString(), "current-mp4-persistent");
        } catch (Exception e) {
            Log.e(TAG, "SAVE_FAILED reason=pick-video-failed"
                + " current=" + current.getAbsolutePath(), e);
            deleteFileIfExists(current, "SAVE_FAILED cleanup-current");
            clearPersistedVideoPath();
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
            File current = getCurrentWallpaperFile();
            String persisted = getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_VIDEO_PATH, null);
            Log.i(TAG, "openLivePicker VIDEO_PATH=" + persisted
                + " CURRENT_EXPECTED=" + current.getAbsolutePath()
                + " FILE_EXISTS=" + current.exists()
                + " FILE_SIZE=" + (current.exists() ? current.length() : -1)
                + " ABSOLUTE_PATH=" + current.getAbsolutePath());

            ValidationResult validation = validateCurrentMp4ForPlayback("openLivePicker");
            if (!validation.ok) {
                Log.e(TAG, "CURRENT_MP4_MISSING reason=" + validation.reason
                    + " path=" + current.getAbsolutePath()
                    + " exists=" + current.exists()
                    + " size=" + (current.exists() ? current.length() : -1));
                call.reject("Archivo de wallpaper no encontrado: " + validation.reason);
                return;
            }

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
        long version = prefs.getLong(KEY_VIDEO_VERSION, 0L);
        long updatedAt = prefs.getLong("video_updated_at", 0L);
        File current = getCurrentWallpaperFile();
        File persisted = path == null ? null : new File(path);
        boolean exists = persisted != null && persisted.exists();
        long size = exists ? persisted.length() : 0;
        boolean canRead = persisted != null && persisted.canRead();
        boolean fdOk = false;
        String fdErr = null;
        if (exists) {
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(persisted, ParcelFileDescriptor.MODE_READ_ONLY)) {
                fdOk = pfd != null;
            } catch (Exception e) {
                fdErr = e.getMessage();
            }
        }
        Log.i(TAG, "getStatus VIDEO_PATH=" + path
            + " CURRENT_EXPECTED=" + current.getAbsolutePath()
            + " FILE_EXISTS=" + exists
            + " FILE_SIZE=" + size
            + " ABSOLUTE_PATH=" + (persisted == null ? "null" : persisted.getAbsolutePath())
            + " canRead=" + canRead
            + " fdOk=" + fdOk
            + " fdErr=" + fdErr
            + " version=" + version
            + " updatedAt=" + updatedAt);

        JSObject ret = new JSObject();
        ret.put("savedPath", path);
        ret.put("expectedCurrentPath", current.getAbsolutePath());
        ret.put("exists", exists);
        ret.put("size", size);
        ret.put("canRead", canRead);
        ret.put("fdOk", fdOk);
        ret.put("version", version);
        ret.put("updatedAt", updatedAt);
        ret.put("renderer", "native-wallpaper-service");
        ret.put("playbackSpeed", 1.0);
        if (fdErr != null) ret.put("fdError", fdErr);
        call.resolve(ret);
    }

    @PluginMethod
    public void checkCompatibility(PluginCall call) {
        WallpaperManager wm = WallpaperManager.getInstance(getContext());
        boolean wallpaperSupported = wm.isWallpaperSupported();
        boolean setAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.N || wm.isSetWallpaperAllowed();
        File current = getCurrentWallpaperFile();
        JSObject ret = new JSObject();
        ret.put("canApplyHome", wallpaperSupported && setAllowed);
        ret.put("canApplyLock", wallpaperSupported && setAllowed);
        ret.put("liveWallpaperSupported", true);
        ret.put("wallpaperSupported", wallpaperSupported);
        ret.put("setWallpaperAllowed", setAllowed);
        ret.put("serviceRegistered", true);
        ret.put("hasVideo", current.exists() && current.length() >= MIN_VALID_VIDEO_BYTES);
        ret.put("isSamsung", "samsung".equalsIgnoreCase(Build.MANUFACTURER));
        ret.put("manufacturer", Build.MANUFACTURER == null ? "" : Build.MANUFACTURER);
        ret.put("sdk", Build.VERSION.SDK_INT);
        ret.put("reason", "ok");
        ret.put("message", "Live wallpaper service registered");
        call.resolve(ret);
    }

    private File getWallpaperDir() {
        File dir = new File(getContext().getFilesDir(), WALLPAPER_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "wallpaper-dir-mkdirs-failed path=" + dir.getAbsolutePath());
        }
        return dir;
    }

    private File getCurrentWallpaperFile() {
        return new File(getWallpaperDir(), CURRENT_MP4);
    }

    private void prepareForNewWallpaper(String wallpaperId) {
        File current = getCurrentWallpaperFile();
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long version = prefs.getLong(KEY_VIDEO_VERSION, 0L) + 1L;
        Log.i(TAG, "DELETE_OLD_WALLPAPER wallpaperId=" + wallpaperId
            + " path=" + current.getAbsolutePath()
            + " exists=" + current.exists()
            + " size=" + (current.exists() ? current.length() : -1));
        deleteFileIfExists(current, "DELETE_OLD_WALLPAPER current.mp4");
        deleteConvertedDirIfExists();
        prefs.edit()
            .remove(KEY_VIDEO_PATH)
            .remove("last_transcode_error")
            .putLong("video_updated_at", System.currentTimeMillis())
            .putLong(KEY_VIDEO_VERSION, version)
            .commit();
    }

    private File commitValidatedCurrentMp4(File current, String wallpaperId, String sourceLabel) throws Exception {
        Log.i(TAG, "CURRENT_MP4_CREATED wallpaperId=" + wallpaperId
            + " source=" + sourceLabel
            + " path=" + current.getAbsolutePath()
            + " exists=" + current.exists()
            + " size=" + (current.exists() ? current.length() : -1));

        ValidationResult currentValidation = validatePhysicalFileForPlayback(current, "current-before-persist", wallpaperId);
        if (!currentValidation.ok) {
            Log.e(TAG, "CURRENT_MP4_MISSING reason=" + currentValidation.reason
                + " path=" + current.getAbsolutePath()
                + " exists=" + current.exists()
                + " size=" + (current.exists() ? current.length() : -1));
            throw new Exception(currentValidation.reason);
        }

        persistCurrentPath(current, wallpaperId);
        Log.i(TAG, "SAVE_SUCCESS wallpaperId=" + wallpaperId
            + " KEY_VIDEO_PATH=" + current.getAbsolutePath()
            + " FILE_EXISTS=" + current.exists()
            + " FILE_SIZE=" + current.length()
            + " ABSOLUTE_PATH=" + current.getAbsolutePath());
        return current;
    }

    private void persistCurrentPath(File current, String wallpaperId) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long version = prefs.getLong(KEY_VIDEO_VERSION, 0L) + 1L;
        prefs.edit()
            .putString(KEY_VIDEO_PATH, current.getAbsolutePath())
            .remove("last_transcode_error")
            .putLong("video_updated_at", System.currentTimeMillis())
            .putLong(KEY_VIDEO_VERSION, version)
            .commit();
        Log.i(TAG, "persistCurrentPath wallpaperId=" + wallpaperId
            + " KEY_VIDEO_PATH=" + prefs.getString(KEY_VIDEO_PATH, null)
            + " version=" + version);
    }

    private void clearPersistedVideoPath() {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long version = prefs.getLong(KEY_VIDEO_VERSION, 0L) + 1L;
        prefs.edit()
            .remove(KEY_VIDEO_PATH)
            .putLong("video_updated_at", System.currentTimeMillis())
            .putLong(KEY_VIDEO_VERSION, version)
            .commit();
    }

    private ValidationResult validateCurrentMp4ForPlayback(String label) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_VIDEO_PATH, null);
        File current = getCurrentWallpaperFile();
        Log.i(TAG, "VIDEO_PATH=" + path);
        Log.i(TAG, "FILE_EXISTS=" + current.exists());
        Log.i(TAG, "FILE_SIZE=" + (current.exists() ? current.length() : -1));
        Log.i(TAG, "ABSOLUTE_PATH=" + current.getAbsolutePath());
        if (path == null) return ValidationResult.fail("key-video-path-empty");
        try {
            if (!new File(path).getCanonicalPath().equals(current.getCanonicalPath())) {
                return ValidationResult.fail("key-video-path-not-current-mp4:" + path);
            }
        } catch (Exception e) {
            return ValidationResult.fail("canonical-path-failed:" + e.getMessage());
        }
        return validatePhysicalFileForPlayback(current, label, "current.mp4");
    }

    private ValidationResult validatePhysicalFileForPlayback(File file, String label, String wallpaperId) {
        if (file == null) return ValidationResult.fail("file-null");
        Log.i(TAG, "validatePhysicalFile label=" + label
            + " wallpaperId=" + wallpaperId
            + " VIDEO_PATH=" + file.getAbsolutePath()
            + " FILE_EXISTS=" + file.exists()
            + " FILE_SIZE=" + (file.exists() ? file.length() : -1)
            + " ABSOLUTE_PATH=" + file.getAbsolutePath()
            + " canRead=" + file.canRead());
        if (!file.exists()) return ValidationResult.fail("file-missing");
        if (!file.canRead()) return ValidationResult.fail("file-not-readable");
        if (file.length() <= MIN_VALID_VIDEO_BYTES) return ValidationResult.fail("file-too-small:" + file.length());

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(getContext(), Uri.fromFile(file));
            long durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            Log.i(TAG, "FILE_DURATION=" + durationMs
                + " VIDEO_PATH=" + file.getAbsolutePath()
                + " size=" + width + "x" + height);
            if (durationMs <= 0L) return ValidationResult.fail("duration-zero");
        } catch (Throwable t) {
            Log.e(TAG, "metadata-validation-failed path=" + file.getAbsolutePath(), t);
            return ValidationResult.fail("metadata-unreadable:" + t.getMessage());
        } finally {
            try { retriever.release(); } catch (Throwable ignored) {}
        }

        if (!canPrepareWithMediaPlayer(file, wallpaperId, label)) {
            return ValidationResult.fail("mediaplayer-prepare-failed");
        }
        return ValidationResult.ok();
    }

    private boolean canPrepareWithMediaPlayer(File file, String wallpaperId, String label) {
        MediaPlayer mp = null;
        try {
            if (file == null || !file.exists() || file.length() <= MIN_VALID_VIDEO_BYTES) {
                Log.e(TAG, "MEDIAPLAYER_PREPARE_FAILED wallpaperId=" + wallpaperId
                    + " label=" + label
                    + " reason=file-missing-or-small"
                    + " VIDEO_PATH=" + (file == null ? "null" : file.getAbsolutePath())
                    + " FILE_EXISTS=" + (file != null && file.exists())
                    + " FILE_SIZE=" + (file != null && file.exists() ? file.length() : -1));
                return false;
            }
            mp = new MediaPlayer();
            mp.setDataSource(getContext(), Uri.fromFile(file));
            mp.setVolume(0f, 0f);
            mp.prepare();
            Log.i(TAG, "MEDIAPLAYER_PREPARE_OK wallpaperId=" + wallpaperId
                + " label=" + label
                + " durationMs=" + mp.getDuration()
                + " VIDEO_PATH=" + file.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "MEDIAPLAYER_PREPARE_FAILED wallpaperId=" + wallpaperId
                + " label=" + label
                + " VIDEO_PATH=" + (file == null ? "null" : file.getAbsolutePath()), t);
            return false;
        } finally {
            if (mp != null) {
                try { mp.release(); } catch (Throwable ignored) {}
            }
        }
    }

    private void resolveSaved(PluginCall call, File file, boolean transcoded, String sourceUri, String reason) {
        JSObject ret = new JSObject();
        ret.put("path", file.getAbsolutePath());
        ret.put("bytes", file.length());
        ret.put("transcoded", transcoded);
        ret.put("usingOriginal", !transcoded);
        ret.put("reason", reason);
        if (sourceUri != null) ret.put("sourceUri", sourceUri);
        call.resolve(ret);
    }

    private void deleteFileIfExists(File file, String label) {
        if (file == null) return;
        try {
            File root = getContext().getFilesDir();
            String rootPath = root.getCanonicalPath();
            String targetPath = file.getCanonicalPath();
            if (!targetPath.startsWith(rootPath)) {
                Log.w(TAG, label + " refused-outside-filesDir path=" + targetPath);
                return;
            }
            boolean existed = file.exists();
            long size = existed ? file.length() : -1;
            boolean deleted = !existed || file.delete();
            Log.i(TAG, label + " path=" + file.getAbsolutePath()
                + " existed=" + existed
                + " size=" + size
                + " deleted=" + deleted);
        } catch (Throwable t) {
            Log.w(TAG, label + " failed path=" + file.getAbsolutePath() + " error=" + t.getMessage());
        }
    }

    private void deleteConvertedDirIfExists() {
        File convertedDir = new File(getWallpaperDir(), "converted");
        File[] files = convertedDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            deleteFileIfExists(file, "DELETE_OLD_WALLPAPER converted-legacy");
        }
        boolean removed = convertedDir.delete();
        Log.i(TAG, "DELETE_OLD_WALLPAPER converted-dir path=" + convertedDir.getAbsolutePath()
            + " removed=" + removed);
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
            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(out, false)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    total += n;
                }
                fos.getFD().sync();
            } finally {
                conn.disconnect();
            }
            return total;
        }
    }

    private long parseLong(String value) {
        try { return value == null ? 0L : Long.parseLong(value); }
        catch (Throwable ignored) { return 0L; }
    }

    private int parseInt(String value) {
        try { return value == null ? 0 : Integer.parseInt(value); }
        catch (Throwable ignored) { return 0; }
    }

    private static final class ValidationResult {
        final boolean ok;
        final String reason;

        private ValidationResult(boolean ok, String reason) {
            this.ok = ok;
            this.reason = reason;
        }

        static ValidationResult ok() { return new ValidationResult(true, "ok"); }
        static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
    }
}