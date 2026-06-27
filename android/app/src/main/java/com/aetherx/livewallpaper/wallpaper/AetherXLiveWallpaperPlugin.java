package com.aetherx.livewallpaper.wallpaper;

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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.StatFs;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.aetherx.livewallpaper.BuildConfig;
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
    public static final String KEY_LAST_SOURCE_URL = "last_source_url";
    public static final String KEY_LAST_DOWNLOAD_BYTES = "last_download_bytes";
    public static final String KEY_LAST_ERROR = "last_error";
    public static final String KEY_OPEN_PICKER_CALLED = "open_picker_called";
    public static final String KEY_CURRENT_ACTION = "current_action";
    public static final String KEY_LAST_EXCEPTION_STACKTRACE = "last_exception_stacktrace";
    public static final String KEY_LAST_STEP = "last_step";

    private static final int MAX_REDIRECTS = 5;
    private static final long MIN_VALID_VIDEO_BYTES = 1024L * 1024L;
    private static final String WALLPAPER_DIR = "AetherX";
    private static final String CURRENT_MP4 = "current.mp4";
    // Reduced from 10 GB (unrealistic — blocked all devices) to 200 MB which fits a 720p ~60s MP4.
    private static final long MIN_FREE_SPACE_BYTES = 200L * 1024L * 1024L;
    private static final String LOW_STORAGE_MESSAGE = "Espacio insuficiente para procesar wallpapers 3D";

    private void setStep(String step) {
        Log.i(TAG, "STEP=" + step);
        try {
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_STEP, step).commit();
        } catch (Throwable ignored) {}
    }

    private void setCurrentAction(String action) {
        Log.i(TAG, "CURRENT_ACTION=" + action);
        try {
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CURRENT_ACTION, action).commit();
        } catch (Throwable ignored) {}
    }

    private void setLastExceptionStacktrace(Throwable t) {
        if (t == null) return;
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            String trace = sw.toString();
            if (trace.length() > 4000) trace = trace.substring(0, 4000);
            Log.e(TAG, "LAST_EXCEPTION_STACKTRACE\n" + trace);
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_EXCEPTION_STACKTRACE, trace).commit();
        } catch (Throwable ignored) {}
    }

    @Override
    public void load() {
        super.load();
        Log.i(TAG, "PLUGIN_LOADED APP_BUILD_VERSION=" + BuildConfig.AETHERX_BUILD_VERSION
            + " " + BuildConfig.AETHERX_BUILD_MARKER);
    }

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

    /** Removes orphan files from Android/data/<package>/files/Movies/AetherX. */
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
        Log.e(TAG, "SAVE_VIDEO_FROM_URL_ENTERED");
        setCurrentAction("saveVideoFromUrl");
        setStep("SAVE_VIDEO_FROM_URL_ENTERED");
        final String url = call.getString("url");
        final String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        final String wallpaperId = call.getString("wallpaperId", fileName);
        Log.i(TAG, "saveVideoFromUrl wallpaperId=" + wallpaperId + " url=" + url + " fileName=" + fileName);
        if (url == null || url.isEmpty()) {
            Log.e(TAG, "SAVE_FAILED reason=missing-url wallpaperId=" + wallpaperId);
            call.reject("missing-url");
            return;
        }
        String storageError = guardStorageOrReject("saveVideoFromUrl");
        if (storageError != null) { call.reject(storageError); return; }

        new Thread(() -> {
            PowerManager.WakeLock wakeLock = acquireShortWakeLock("saveVideoFromUrl");
            File current = getCurrentWallpaperFile();
            try {
                prepareForNewWallpaper(wallpaperId);
                persistLastSourceUrl(url, wallpaperId);
                clearLastError();
                setLastDownloadBytes(0L);
                setOpenPickerCalled(false);
                Log.i(TAG, "download-start wallpaperId=" + wallpaperId + " current=" + current.getAbsolutePath());
                long bytes = downloadFollowingRedirects(url, current);
                setLastDownloadBytes(bytes);
                Log.i(TAG, "download-complete wallpaperId=" + wallpaperId
                    + " bytes=" + bytes
                    + " exists=" + current.exists()
                    + " size=" + current.length()
                    + " absolute=" + current.getAbsolutePath());

                current = commitValidatedCurrentMp4(current, wallpaperId, "download");
                resolveSaved(call, current, false, null, "current-mp4-persistent");
            } catch (Exception e) {
                setLastError(classifySaveError(e) + ": " + e.getMessage());
                Log.e(TAG, "SAVE_FAILED wallpaperId=" + wallpaperId
                    + " current=" + current.getAbsolutePath(), e);
                Log.e(TAG, "CURRENT_MP4_SAVE_FAILED wallpaperId=" + wallpaperId
                    + " PATH=" + current.getAbsolutePath()
                    + " EXISTS=" + current.exists()
                    + " CAN_READ=" + current.canRead()
                    + " SIZE=" + (current.exists() ? current.length() : -1)
                    + " ABSOLUTE_PATH=" + current.getAbsolutePath(), e);
                deleteFileIfExists(current, "SAVE_FAILED cleanup-current");
                clearPersistedVideoPath();
                call.reject(classifySaveError(e), e);
            } finally {
                releaseWakeLock(wakeLock, "saveVideoFromUrl");
            }
        }).start();
    }

    @PluginMethod
    public void saveVideoFromUrlAndOpenPicker(final PluginCall call) {
        Log.e(TAG, "SAVE_VIDEO_FROM_URL_AND_OPEN_PICKER_ENTERED");
        setCurrentAction("saveVideoFromUrlAndOpenPicker");
        setStep("START_SAVE_VIDEO");
        final String url = call.getString("url");
        final String fileName = sanitizeFileName(call.getString("fileName", "wallpaper.mp4"));
        final String wallpaperId = call.getString("wallpaperId", fileName);
        Log.i(TAG, "START_SAVE_VIDEO wallpaperId=" + wallpaperId + " url=" + url + " fileName=" + fileName);
        if (url == null || url.isEmpty()) {
            setStep("DOWNLOAD_FAILED:missing-url");
            setLastError("descarga fallida: missing-url");
            setOpenPickerCalled(false);
            call.reject("descarga fallida");
            return;
        }
        String storageError = guardStorageOrReject("saveVideoFromUrlAndOpenPicker");
        if (storageError != null) {
            setStep("DOWNLOAD_FAILED:low-storage");
            setLastError(storageError);
            setOpenPickerCalled(false);
            call.reject(storageError);
            return;
        }

        new Thread(() -> {
            PowerManager.WakeLock wakeLock = acquireShortWakeLock("saveVideoFromUrlAndOpenPicker");
            File current = getCurrentWallpaperFile();
            try {
                prepareForNewWallpaper(wallpaperId);
                persistLastSourceUrl(url, wallpaperId);
                setStep("SET_KEY_VIDEO_PATH:pending");
                clearLastError();
                setLastDownloadBytes(0L);
                setOpenPickerCalled(false);

                File parent = current.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new Exception("parent-dir-unavailable:" + parent.getAbsolutePath());
                }
                Log.i(TAG, "PARENT_DIR_READY"
                    + " parent=" + (parent == null ? "null" : parent.getAbsolutePath())
                    + " parentExists=" + (parent != null && parent.exists())
                    + " parentWritable=" + (parent != null && parent.canWrite()));

                // Samsung OneUI rejects manual file preflight on some devices; the stream creates the file.
                Log.i(TAG, "PRE_STREAM path=" + current.getAbsolutePath()
                    + " exists=" + current.exists()
                    + " canWrite=" + current.canWrite()
                    + " parentCanWrite=" + (parent != null && parent.canWrite()));

                setStep("DOWNLOAD_STARTED");
                Log.i(TAG, "DOWNLOAD_STARTED wallpaperId=" + wallpaperId
                    + " current=" + current.getAbsolutePath() + " url=" + url);
                long bytes = downloadFollowingRedirects(url, current);
                setLastDownloadBytes(bytes);
                setStep("DOWNLOAD_SUCCESS:" + bytes);
                Log.i(TAG, "DOWNLOAD_SUCCESS wallpaperId=" + wallpaperId
                    + " bytes=" + bytes
                    + " exists=" + current.exists()
                    + " size=" + current.length()
                    + " absolute=" + current.getAbsolutePath());

                setStep("WRITE_SUCCESS");
                current = commitValidatedCurrentMp4(current, wallpaperId, "download-and-open");
                setStep("SET_KEY_VIDEO_PATH:" + current.getAbsolutePath());
                setStep("SAVE_COMPLETE");
                final String finalPath = current.getAbsolutePath();
                new Handler(Looper.getMainLooper()).post(() -> openLivePickerForFinalPath(call, finalPath));
            } catch (Exception e) {
                setStep("DOWNLOAD_FAILED:" + e.getMessage());
                String userError = classifySaveError(e);
                setLastError(userError + ": " + e.getMessage());
                setLastExceptionStacktrace(e);
                setOpenPickerCalled(false);
                Log.e(TAG, "SAVE_AND_OPEN_FAILED wallpaperId=" + wallpaperId
                    + " error=" + userError
                    + " current=" + current.getAbsolutePath(), e);
                Log.e(TAG, "CURRENT_MP4_SAVE_FAILED wallpaperId=" + wallpaperId
                    + " PATH=" + current.getAbsolutePath()
                    + " EXISTS=" + current.exists()
                    + " CAN_READ=" + current.canRead()
                    + " SIZE=" + (current.exists() ? current.length() : -1)
                    + " ABSOLUTE_PATH=" + current.getAbsolutePath(), e);
                deleteFileIfExists(current, "SAVE_AND_OPEN_FAILED cleanup-current");
                clearPersistedVideoPath();
                call.reject(userError, e);
            } finally {
                releaseWakeLock(wakeLock, "saveVideoFromUrlAndOpenPicker");
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
        String storageError = guardStorageOrReject("saveVideo");
        if (storageError != null) { call.reject(storageError); return; }

        File current = getCurrentWallpaperFile();
        PowerManager.WakeLock wakeLock = acquireShortWakeLock("saveVideo");
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
            Log.e(TAG, "CURRENT_MP4_SAVE_FAILED fileName=" + fileName
                + " PATH=" + current.getAbsolutePath()
                + " EXISTS=" + current.exists()
                + " CAN_READ=" + current.canRead()
                + " SIZE=" + (current.exists() ? current.length() : -1)
                + " ABSOLUTE_PATH=" + current.getAbsolutePath(), e);
            deleteFileIfExists(current, "SAVE_FAILED cleanup-current");
            clearPersistedVideoPath();
            call.reject("save-failed: " + e.getMessage(), e);
        } finally {
            releaseWakeLock(wakeLock, "saveVideo");
        }
    }

    @PluginMethod
    public void pickVideoFromDevice(PluginCall call) {
        Log.i(TAG, "pickVideoFromDevice opening ACTION_OPEN_DOCUMENT copy-to-externalMovies-current-only=true");
        String storageError = guardStorageOrReject("pickVideoFromDevice");
        if (storageError != null) { call.reject(storageError); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "onPickVideoResult");
    }

    @PluginMethod
    public void checkStorage(PluginCall call) {
        File dir = getWallpaperDir();
        StatFs stat = new StatFs(dir.getAbsolutePath());
        long freeBytes = stat.getAvailableBytes();
        long freeMb = freeBytes / (1024L * 1024L);
        long requiredMb = MIN_FREE_SPACE_BYTES / (1024L * 1024L);
        boolean ok = freeBytes >= MIN_FREE_SPACE_BYTES;
        Log.i(TAG, "FREE_SPACE_MB=" + freeMb + " stage=checkStorage requiredMb=" + requiredMb + " ok=" + ok);
        if (!ok) Log.w(TAG, "LOW_STORAGE_WARNING freeMb=" + freeMb + " requiredMb=" + requiredMb);
        JSObject ret = new JSObject();
        ret.put("ok", ok);
        ret.put("freeMb", freeMb);
        ret.put("requiredMb", requiredMb);
        ret.put("message", ok ? "ok" : LOW_STORAGE_MESSAGE);
        call.resolve(ret);
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
        Log.i(TAG, "onPickVideoResult selectedVideoUri=" + uri + " note=will-copy-to-externalMovies-current-mp4-no-final-content-uri");
        if (uri == null) {
            Log.e(TAG, "SAVE_FAILED reason=pick-video-no-uri");
            call.reject("pick-video-no-uri");
            return;
        }

        File current = getCurrentWallpaperFile();
        PowerManager.WakeLock wakeLock = acquireShortWakeLock("pickVideoFromDevice");
        try {
            persistUriReadPermission(result.getData(), uri);
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
            setLastDownloadBytes(total);
            current = commitValidatedCurrentMp4(current, "picked-video", "picked");
            resolveSaved(call, current, false, uri.toString(), "current-mp4-persistent");
        } catch (Exception e) {
            setLastError("archivo no guardado: " + e.getMessage());
            setOpenPickerCalled(false);
            Log.e(TAG, "SAVE_FAILED reason=pick-video-failed"
                + " current=" + current.getAbsolutePath(), e);
            Log.e(TAG, "CURRENT_MP4_SAVE_FAILED source=picked-video"
                + " PATH=" + current.getAbsolutePath()
                + " EXISTS=" + current.exists()
                + " CAN_READ=" + current.canRead()
                + " SIZE=" + (current.exists() ? current.length() : -1)
                + " ABSOLUTE_PATH=" + current.getAbsolutePath(), e);
            deleteFileIfExists(current, "SAVE_FAILED cleanup-current");
            clearPersistedVideoPath();
            call.reject("pick-video-failed: " + e.getMessage(), e);
        } finally {
            releaseWakeLock(wakeLock, "pickVideoFromDevice");
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
            SharedPreferences prefs = getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String persisted = prefs.getString(KEY_VIDEO_PATH, null);
            Log.i(TAG, "openLivePicker VIDEO_PATH=" + persisted
                + " CURRENT_EXPECTED=" + current.getAbsolutePath()
                + " FILE_EXISTS=" + current.exists()
                + " CAN_READ=" + current.canRead()
                + " FILE_SIZE=" + (current.exists() ? current.length() : -1)
                + " ABSOLUTE_PATH=" + current.getAbsolutePath());

            openLivePickerForFinalPath(call, persisted);
        } catch (Exception e) {
            setLastError("open-picker-failed: " + e.getMessage());
            setOpenPickerCalled(false);
            Log.e(TAG, "open-picker-failed", e);
            call.reject("open-picker-failed: " + e.getMessage(), e);
        }
    }

    private void openLivePickerForFinalPath(PluginCall call, String finalPath) {
        File current = getCurrentWallpaperFile();
        ValidationResult validation = validateFinalPathBeforePicker(finalPath, "before-intent");
        if (!validation.ok) {
            rejectPickerNotReady(call, finalPath, validation.reason);
            return;
        }

        Log.i(TAG, "CURRENT_MP4_EXISTS=" + current.exists() + " PATH=" + current.getAbsolutePath());
        Log.i(TAG, "CURRENT_MP4_CAN_READ=" + current.canRead() + " PATH=" + current.getAbsolutePath());

        ComponentName comp = new ComponentName(
            getContext().getPackageName(),
            AetherXLiveWallpaperService.class.getName()
        );
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.i(TAG, "WALLPAPER_OPEN_DELAY ms=500 PATH=" + finalPath);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                ValidationResult delayedValidation = validateFinalPathBeforePicker(finalPath, "after-500ms-delay");
                if (!delayedValidation.ok) {
                    rejectPickerNotReady(call, finalPath, delayedValidation.reason);
                    return;
                }
                setOpenPickerCalled(true);
                getContext().startActivity(intent);
                JSObject ret = new JSObject();
                ret.put("applied", false);
                ret.put("openedPicker", true);
                ret.put("needsConfirmation", true);
                ret.put("opened", true);
                ret.put("path", finalPath);
                ret.put("bytes", new File(finalPath).length());
                call.resolve(ret);
            } catch (Exception e) {
                setLastError("open-picker-failed: " + e.getMessage());
                setOpenPickerCalled(false);
                Log.e(TAG, "open-picker-failed-delayed", e);
                call.reject("open-picker-failed: " + e.getMessage(), e);
            }
        }, 500L);
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String path = prefs.getString(KEY_VIDEO_PATH, null);
        String lastDownloadUrl = prefs.getString(KEY_LAST_SOURCE_URL, null);
        long lastDownloadBytes = prefs.getLong(KEY_LAST_DOWNLOAD_BYTES, 0L);
        String lastError = prefs.getString(KEY_LAST_ERROR, null);
        boolean openPickerCalled = prefs.getBoolean(KEY_OPEN_PICKER_CALLED, false);
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
        ret.put("finalPath", current.getAbsolutePath());
        ret.put("fileExists", current.exists());
        ret.put("fileSize", current.exists() ? current.length() : 0L);
        ret.put("canRead", current.exists() && current.canRead());
        ret.put("KEY_VIDEO_PATH", path);
        ret.put("lastDownloadUrl", lastDownloadUrl);
        ret.put("lastDownloadBytes", lastDownloadBytes);
        ret.put("lastError", lastError);
        ret.put("openPickerCalled", openPickerCalled);
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
        ret.put("pluginAvailable", true);
        ret.put("currentAction", prefs.getString(KEY_CURRENT_ACTION, null));
        ret.put("lastStep", prefs.getString(KEY_LAST_STEP, null));
        ret.put("lastExceptionStacktrace", prefs.getString(KEY_LAST_EXCEPTION_STACKTRACE, null));
        File parent = current.getParentFile();
        ret.put("parentDir", parent == null ? null : parent.getAbsolutePath());
        ret.put("parentExists", parent != null && parent.exists());
        ret.put("parentWritable", parent != null && parent.canWrite());
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
        File moviesDir = getContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (moviesDir == null) {
            throw new IllegalStateException("external-movies-dir-unavailable");
        }
        File dir = new File(moviesDir, WALLPAPER_DIR);
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
        cleanOrphanWallpaperFiles("prepareForNewWallpaper");
        prefs.edit()
            .remove(KEY_VIDEO_PATH)
            .remove("last_transcode_error")
            .putLong("video_updated_at", System.currentTimeMillis())
            .putLong(KEY_VIDEO_VERSION, version)
            .commit();
    }

    private File commitValidatedCurrentMp4(File current, String wallpaperId, String sourceLabel) throws Exception {
        waitForClosedFile(current, wallpaperId, sourceLabel);
        current = new File(current.getAbsolutePath());
        Log.i(TAG, "CURRENT_MP4_CREATED wallpaperId=" + wallpaperId
            + " source=" + sourceLabel
            + " path=" + current.getAbsolutePath()
            + " exists=" + current.exists()
            + " canRead=" + current.canRead()
            + " size=" + (current.exists() ? current.length() : -1));

        ValidationResult currentValidation = validatePhysicalFileForPlayback(current, "current-before-persist", wallpaperId);
        if (!currentValidation.ok) {
            Log.e(TAG, "CURRENT_MP4_MISSING reason=" + currentValidation.reason
                + " path=" + current.getAbsolutePath()
                + " exists=" + current.exists()
                + " size=" + (current.exists() ? current.length() : -1));
            Log.e(TAG, "CURRENT_MP4_SAVE_FAILED wallpaperId=" + wallpaperId
                + " reason=" + currentValidation.reason
                + " PATH=" + current.getAbsolutePath()
                + " EXISTS=" + current.exists()
                + " CAN_READ=" + current.canRead()
                + " SIZE=" + (current.exists() ? current.length() : -1)
                + " ABSOLUTE_PATH=" + current.getAbsolutePath());
            throw new Exception(currentValidation.reason);
        }

        persistCurrentPath(current, wallpaperId);
        Log.i(TAG, "CURRENT_MP4_SAVE_OK wallpaperId=" + wallpaperId
            + " PATH=" + current.getAbsolutePath()
            + " EXISTS=" + current.exists()
            + " CAN_READ=" + current.canRead()
            + " SIZE=" + current.length()
            + " ABSOLUTE_PATH=" + current.getAbsolutePath());
        Log.i(TAG, "CURRENT_MP4_EXISTS=" + current.exists() + " PATH=" + current.getAbsolutePath());
        Log.i(TAG, "CURRENT_MP4_CAN_READ=" + current.canRead() + " PATH=" + current.getAbsolutePath());
        Log.i(TAG, "SAVE_SUCCESS wallpaperId=" + wallpaperId
            + " KEY_VIDEO_PATH=" + current.getAbsolutePath()
            + " FILE_EXISTS=" + current.exists()
            + " CAN_READ=" + current.canRead()
            + " FILE_SIZE=" + current.length()
            + " ABSOLUTE_PATH=" + current.getAbsolutePath());
        return current;
    }

    private void waitForClosedFile(File current, String wallpaperId, String sourceLabel) throws Exception {
        if (current == null) throw new Exception("archivo no guardado");
        long firstSize = current.exists() ? current.length() : -1L;
        try { Thread.sleep(250L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        File reread = new File(current.getAbsolutePath());
        long secondSize = reread.exists() ? reread.length() : -1L;
        Log.i(TAG, "CURRENT_MP4_REREAD wallpaperId=" + wallpaperId
            + " source=" + sourceLabel
            + " PATH=" + reread.getAbsolutePath()
            + " EXISTS=" + reread.exists()
            + " CAN_READ=" + reread.canRead()
            + " SIZE_FIRST=" + firstSize
            + " SIZE_SECOND=" + secondSize);
        if (!reread.exists()) throw new Exception("archivo no guardado");
        if (secondSize <= MIN_VALID_VIDEO_BYTES) throw new Exception("archivo no guardado: file-too-small:" + secondSize);
        if (!reread.canRead()) throw new Exception("archivo no guardado: file-not-readable");
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

    private void persistLastSourceUrl(String url, String wallpaperId) {
        if (url == null || url.isEmpty()) return;
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_LAST_SOURCE_URL, url)
            .commit();
        Log.i(TAG, "persistLastSourceUrl wallpaperId=" + wallpaperId + " hasUrl=true");
    }

    private void setLastDownloadBytes(long bytes) {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_DOWNLOAD_BYTES, bytes)
            .commit();
    }

    private void setLastError(String error) {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ERROR, error)
            .commit();
    }

    private void clearLastError() {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_ERROR)
            .commit();
    }

    private void setOpenPickerCalled(boolean called) {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OPEN_PICKER_CALLED, called)
            .commit();
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
        Log.i(TAG, "PATH=" + path);
        Log.i(TAG, "EXISTS=" + current.exists());
        Log.i(TAG, "CAN_READ=" + current.canRead());
        Log.i(TAG, "SIZE=" + (current.exists() ? current.length() : -1));
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
            + " PATH=" + file.getAbsolutePath()
            + " EXISTS=" + file.exists()
            + " CAN_READ=" + file.canRead()
            + " SIZE=" + (file.exists() ? file.length() : -1)
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

    private ValidationResult validateFinalPathBeforePicker(String finalPath, String stage) {
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File current = getCurrentWallpaperFile();
        String persisted = prefs.getString(KEY_VIDEO_PATH, null);
        if (finalPath == null || finalPath.isEmpty()) {
            return ValidationResult.fail("CURRENT_MP4_NOT_READY:key-video-path-empty:" + stage);
        }
        if (persisted == null || !persisted.equals(finalPath)) {
            return ValidationResult.fail("CURRENT_MP4_NOT_READY:key-video-path-mismatch:" + stage);
        }
        try {
            if (!new File(finalPath).getCanonicalPath().equals(current.getCanonicalPath())) {
                return ValidationResult.fail("CURRENT_MP4_NOT_READY:not-current-mp4:" + stage);
            }
        } catch (Exception e) {
            return ValidationResult.fail("CURRENT_MP4_NOT_READY:canonical-path-failed:" + stage + ":" + e.getMessage());
        }

        File file = new File(finalPath);
        Log.i(TAG, "CURRENT_MP4_STRICT_PICKER_CHECK stage=" + stage
            + " PATH=" + finalPath
            + " EXISTS=" + file.exists()
            + " CAN_READ=" + file.canRead()
            + " SIZE=" + (file.exists() ? file.length() : -1)
            + " KEY_VIDEO_PATH=" + persisted);

        if (!file.exists() || file.length() < 1024 * 1024) {
            return ValidationResult.fail("CURRENT_MP4_NOT_READY");
        }
        if (!file.canRead()) {
            return ValidationResult.fail("CURRENT_MP4_NOT_READY:file-not-readable:" + stage);
        }

        ValidationResult playbackValidation = validateCurrentMp4ForPlayback("openLivePicker-" + stage);
        if (!playbackValidation.ok) {
            return ValidationResult.fail("CURRENT_MP4_NOT_READY:" + playbackValidation.reason);
        }
        return ValidationResult.ok();
    }

    private void rejectPickerNotReady(PluginCall call, String finalPath, String reason) {
        setOpenPickerCalled(false);
        setLastError("CURRENT_MP4_NOT_READY: " + reason);
        File file = finalPath == null ? null : new File(finalPath);
        Log.e(TAG, "CURRENT_MP4_NOT_READY reason=" + reason
            + " PATH=" + finalPath
            + " EXISTS=" + (file != null && file.exists())
            + " CAN_READ=" + (file != null && file.canRead())
            + " SIZE=" + (file != null && file.exists() ? file.length() : -1)
            + " openPickerCalled=false");
        call.reject("CURRENT_MP4_NOT_READY");
    }

    private String classifySaveError(Exception e) {
        String msg = e == null || e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.startsWith("http-")
            || msg.contains("redirect")
            || msg.contains("timed out")
            || msg.contains("timeout")
            || msg.contains("host")
            || msg.contains("network")
            || msg.contains("connection")) {
            return "descarga fallida";
        }
        return "archivo no guardado";
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
            File root = getWallpaperDir();
            String rootPath = root.getCanonicalPath();
            String targetPath = file.getCanonicalPath();
            if (!targetPath.startsWith(rootPath)) {
                Log.w(TAG, label + " refused-outside-wallpaper-dir path=" + targetPath);
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

    private PowerManager.WakeLock acquireShortWakeLock(String stage) {
        try {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            if (pm == null) return null;
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AetherX:WallpaperSave");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(2 * 60 * 1000L);
            Log.i(TAG, "WAKE_LOCK_ACQUIRED stage=" + stage);
            return wakeLock;
        } catch (Throwable t) {
            Log.w(TAG, "WAKE_LOCK_ACQUIRE_FAILED stage=" + stage + " err=" + t.getMessage());
            return null;
        }
    }

    private void releaseWakeLock(PowerManager.WakeLock wakeLock, String stage) {
        if (wakeLock == null) return;
        try {
            if (wakeLock.isHeld()) wakeLock.release();
            Log.i(TAG, "WAKE_LOCK_RELEASED stage=" + stage);
        } catch (Throwable t) {
            Log.w(TAG, "WAKE_LOCK_RELEASE_FAILED stage=" + stage + " err=" + t.getMessage());
        }
    }

    private void persistUriReadPermission(Intent data, Uri uri) {
        if (uri == null || data == null || !"content".equalsIgnoreCase(uri.getScheme())) return;
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContext().getContentResolver().takePersistableUriPermission(uri, flags);
            Log.i(TAG, "PERSISTABLE_URI_PERMISSION_OK uri=" + uri);
        } catch (Throwable t) {
            Log.w(TAG, "PERSISTABLE_URI_PERMISSION_FAILED uri=" + uri + " err=" + t.getMessage());
        }
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
            Log.i(TAG, "OPEN_FILE_OUTPUTSTREAM path=" + out.getAbsolutePath()
                + " existsBefore=" + out.exists()
                + " canWriteBefore=" + out.canWrite()
                + " parentExists=" + (parent != null && parent.exists())
                + " parentCanWrite=" + (parent != null && parent.canWrite()));
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(out, false);
            } catch (Throwable t) {
                Log.e(TAG, "FILE_OUTPUTSTREAM_FAILED path=" + out.getAbsolutePath(), t);
                setLastExceptionStacktrace(t instanceof Exception ? (Exception) t : new Exception(t));
                conn.disconnect();
                throw new Exception("file-output-stream-failed:" + t.getMessage());
            }
            Log.i(TAG, "FILE_OUTPUTSTREAM_OK path=" + out.getAbsolutePath()
                + " existsAfterOpen=" + out.exists()
                + " canWriteAfterOpen=" + out.canWrite());
            try (InputStream in = conn.getInputStream(); FileOutputStream out2 = fos) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out2.write(buf, 0, n);
                    total += n;
                }
                out2.getFD().sync();
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