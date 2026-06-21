package com.aetherx.livewallpaper;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "AetherXLiveWallpaper")
public class AetherXLiveWallpaperPlugin extends Plugin {
    static final String VIDEO_FILE = "aetherx-live-wallpaper.mp4";
    private static final String DEFAULT_MP4_MIME = "video/mp4";
    private static final String CAMERA_GALLERY_PATH = Environment.DIRECTORY_DCIM + "/Camera/";
    private static final String DOWNLOADS_GALLERY_PATH = Environment.DIRECTORY_DOWNLOADS + "/AetherX/";
    private static final String AETHERX_GALLERY_PATH = Environment.DIRECTORY_MOVIES + "/AetherX/";

    @PluginMethod
    public void saveVideoFromUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.length() == 0) {
            call.reject("missing-video-url");
            return;
        }
        String fileName = normalizeFileName(call.getString("fileName"));

        execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    call.reject("video-download-failed-" + responseCode);
                    return;
                }

                File file = new File(getContext().getFilesDir(), VIDEO_FILE);
                int total = 0;
                byte[] buffer = new byte[8192];
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file, false)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        total += read;
                    }
                }

                assertPlayableVideo(file);

                String galleryUri = saveToGallery(file, fileName, DEFAULT_MP4_MIME);
                resolveSaved(call, file, total, galleryUri);
            } catch (Exception e) {
                call.reject("video-download-save-failed", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    @PluginMethod
    public void saveVideo(PluginCall call) {
        String base64 = call.getString("base64");
        if (base64 == null || base64.length() == 0) {
            call.reject("missing-video-data");
            return;
        }

        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            File file = new File(getContext().getFilesDir(), VIDEO_FILE);
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(bytes);
            }

            assertPlayableVideo(file);
            String galleryUri = saveToGallery(file, normalizeFileName(call.getString("fileName")), DEFAULT_MP4_MIME);
            resolveSaved(call, file, bytes.length, galleryUri);
        } catch (Exception e) {
            call.reject("video-save-failed", e);
        }
    }

    /**
     * Abre el explorador de archivos COMPLETO del sistema (Storage Access Framework)
     * para que el usuario pueda navegar TODAS las carpetas del dispositivo
     * (Download, DCIM, Movies, WhatsApp, Telegram, etc.) y elegir cualquier
     * vídeo en cualquier formato (mp4, mov, mkv, webm, avi, 3gp...).
     */
    @PluginMethod
    public void pickVideoFromDevice(PluginCall call) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                new String[] {
                    "video/mp4",
                    "video/quicktime",
                    "video/x-matroska",
                    "video/webm",
                    "video/x-msvideo",
                    "video/3gpp",
                    "video/*"
                }
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Elige un vídeo");
            startActivityForResult(call, chooser, "handlePickedVideo");
        } catch (Exception e) {
            call.reject("pick-video-failed", e);
        }
    }

    @ActivityCallback
    private void handlePickedVideo(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result == null || result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            call.reject("pick-video-cancelled");
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            call.reject("pick-video-no-uri");
            return;
        }

        try {
            ContentResolver resolver = getContext().getContentResolver();
            File destination = new File(getContext().getFilesDir(), VIDEO_FILE);
            int total = 0;
            byte[] buffer = new byte[8192];
            try (InputStream input = resolver.openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(destination, false)) {
                if (input == null) throw new IllegalStateException("cannot-open-input");
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    total += read;
                }
            }

            assertPlayableVideo(destination);
            String sourceMime = resolver.getType(uri);
            String sourceName = getDisplayName(resolver, uri);
            String galleryUri = saveToGallery(destination, normalizeFileName(sourceName), normalizeMimeType(sourceMime, sourceName));

            JSObject obj = new JSObject();
            obj.put("path", destination.getAbsolutePath());
            obj.put("bytes", total);
            obj.put("sourceUri", uri.toString());
            obj.put("galleryUri", galleryUri);
            call.resolve(obj);
        } catch (Exception e) {
            call.reject("pick-video-copy-failed", e);
        }
    }

    @PluginMethod
    public void applyHome(PluginCall call) {
        try {
            File file = new File(getContext().getFilesDir(), VIDEO_FILE);
            if (!file.exists() || file.length() == 0) {
                call.reject("missing-saved-video");
                return;
            }

            WallpaperManager manager = WallpaperManager.getInstance(getContext());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!manager.isWallpaperSupported()) {
                    call.reject("wallpaper-not-supported");
                    return;
                }
                if (!manager.isSetWallpaperAllowed()) {
                    call.reject("set-wallpaper-not-allowed");
                    return;
                }
            }

            ComponentName service = new ComponentName(getContext(), AetherXVideoWallpaperService.class);
            manager.setWallpaperComponent(service);

            WallpaperInfo info = manager.getWallpaperInfo();
            boolean verified = info != null
                && getContext().getPackageName().equals(info.getPackageName())
                && AetherXVideoWallpaperService.class.getName().equals(info.getServiceName());

            JSObject result = new JSObject();
            result.put("applied", true);
            result.put("verified", verified);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("live-wallpaper-home-apply-failed", e);
        }
    }

    @PluginMethod
    public void applyLock(PluginCall call) {
        try {
            File file = new File(getContext().getFilesDir(), VIDEO_FILE);
            if (!file.exists() || file.length() == 0) {
                call.reject("missing-saved-video");
                return;
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                call.reject("lock-screen-not-supported");
                return;
            }
            Bitmap frame = extractFirstFrame(file);
            if (frame == null) {
                call.reject("lock-frame-extract-failed");
                return;
            }
            WallpaperManager manager = WallpaperManager.getInstance(getContext());
            manager.setBitmap(frame, null, true, WallpaperManager.FLAG_LOCK);
            frame.recycle();
            JSObject result = new JSObject();
            result.put("applied", true);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("lock-wallpaper-apply-failed", e);
        }
    }

    @PluginMethod
    public void applyBoth(PluginCall call) {
        try {
            File file = new File(getContext().getFilesDir(), VIDEO_FILE);
            if (!file.exists() || file.length() == 0) {
                call.reject("missing-saved-video");
                return;
            }
            WallpaperManager manager = WallpaperManager.getInstance(getContext());
            ComponentName service = new ComponentName(getContext(), AetherXVideoWallpaperService.class);
            manager.setWallpaperComponent(service);

            boolean lockApplied = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Bitmap frame = extractFirstFrame(file);
                if (frame != null) {
                    try {
                        manager.setBitmap(frame, null, true, WallpaperManager.FLAG_LOCK);
                        lockApplied = true;
                    } finally {
                        frame.recycle();
                    }
                }
            }

            WallpaperInfo info = manager.getWallpaperInfo();
            boolean verifiedHome = info != null
                && getContext().getPackageName().equals(info.getPackageName())
                && AetherXVideoWallpaperService.class.getName().equals(info.getServiceName());

            JSObject result = new JSObject();
            result.put("applied", true);
            result.put("homeVerified", verifiedHome);
            result.put("lockApplied", lockApplied);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("both-wallpaper-apply-failed", e);
        }
    }

    private Bitmap extractFirstFrame(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            // First playable frame, scaled to typical phone resolution for memory safety.
            Bitmap raw = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            return raw;
        } catch (Exception e) {
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    @PluginMethod
    public void openPicker(PluginCall call) {
        try {
            ComponentName service = new ComponentName(getContext(), AetherXVideoWallpaperService.class);
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, service);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);

            JSObject result = new JSObject();
            result.put("opened", true);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("live-wallpaper-picker-failed", e);
        }
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        File file = new File(getContext().getFilesDir(), VIDEO_FILE);
        JSObject result = new JSObject();
        result.put("available", true);
        result.put("hasVideo", file.exists() && file.length() > 0);
        call.resolve(result);
    }

    @PluginMethod
    public void checkCompatibility(PluginCall call) {
        JSObject result = new JSObject();
        boolean liveWallpaperSupported = getContext()
            .getPackageManager()
            .hasSystemFeature("android.software.live_wallpaper");
        WallpaperManager wm = WallpaperManager.getInstance(getContext());
        boolean wallpaperSupported = wm.isWallpaperSupported();
        boolean setWallpaperAllowed = true;
        try {
            setWallpaperAllowed = wm.isSetWallpaperAllowed();
        } catch (Throwable ignored) {}

        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        boolean isSamsung = manufacturer.toLowerCase().contains("samsung");
        int sdk = Build.VERSION.SDK_INT;
        File file = new File(getContext().getFilesDir(), VIDEO_FILE);
        boolean hasVideo = file.exists() && file.length() > 0;

        boolean serviceRegistered = false;
        try {
            ComponentName cn = new ComponentName(
                getContext(),
                AetherXVideoWallpaperService.class
            );
            serviceRegistered = getContext()
                .getPackageManager()
                .getServiceInfo(cn, 0) != null;
        } catch (Throwable ignored) {}

        boolean canApplyHome = liveWallpaperSupported && wallpaperSupported && setWallpaperAllowed && serviceRegistered;

        String reason = "ok";
        String message = "Compatible";
        if (!liveWallpaperSupported) {
            reason = "no-live-wallpaper-feature";
            message = "Tu dispositivo no admite fondos animados (Live Wallpaper).";
        } else if (!wallpaperSupported) {
            reason = "wallpaper-not-supported";
            message = "Este dispositivo no permite cambiar el fondo de pantalla.";
        } else if (!setWallpaperAllowed) {
            reason = "wallpaper-blocked";
            message = "Tu administrador o sistema ha bloqueado el cambio de fondo.";
        } else if (!serviceRegistered) {
            reason = "service-missing";
            message = "El servicio de AetherX Live Wallpaper no está registrado.";
        } else if (!hasVideo) {
            reason = "no-video";
            message = "Primero descarga o selecciona un vídeo.";
        } else if (isSamsung) {
            message = "Compatible. En Samsung, elige \"Pantalla de inicio\" en el selector.";
        }

        result.put("canApplyHome", canApplyHome);
        result.put("canApplyLock", wallpaperSupported && setWallpaperAllowed);
        result.put("liveWallpaperSupported", liveWallpaperSupported);
        result.put("wallpaperSupported", wallpaperSupported);
        result.put("setWallpaperAllowed", setWallpaperAllowed);
        result.put("serviceRegistered", serviceRegistered);
        result.put("hasVideo", hasVideo);
        result.put("isSamsung", isSamsung);
        result.put("manufacturer", manufacturer);
        result.put("sdk", sdk);
        result.put("reason", reason);
        result.put("message", message);
        call.resolve(result);
    }

    private void resolveSaved(PluginCall call, File file, int bytes, String galleryUri) {
        JSObject result = new JSObject();
        result.put("path", file.getAbsolutePath());
        result.put("bytes", bytes);
        result.put("galleryUri", galleryUri);
        call.resolve(result);
    }

    private static class VideoMetadata {
        long size;
        long durationMs;
        int width;
        int height;
    }

    private String normalizeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        if (name.length() == 0) name = "aetherx-live-wallpaper.mp4";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "-");
        String lower = name.toLowerCase();
        if (
            !lower.endsWith(".mp4") &&
            !lower.endsWith(".mov") &&
            !lower.endsWith(".mkv") &&
            !lower.endsWith(".webm") &&
            !lower.endsWith(".avi") &&
            !lower.endsWith(".3gp")
        ) name = name + ".mp4";
        return name;
    }

    private String saveToGallery(File source, String fileName, String mimeType) throws Exception {
        VideoMetadata metadata = readVideoMetadata(source);
        String visibleName = makeFreshGalleryName(fileName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = insertGalleryVideo(source, visibleName, mimeType, CAMERA_GALLERY_PATH, metadata);
            // Secondary indexed copies: Samsung's wallpaper "Seleccionar elemento" can read from
            // Recientes/Descargas while Gallery reads DCIM/Camera or Movies.
            try {
                insertDownloadCopy(source, visibleName, mimeType, metadata);
            } catch (Exception ignored) {}
            try {
                insertGalleryVideo(source, "AetherX-" + visibleName, mimeType, AETHERX_GALLERY_PATH, metadata);
            } catch (Exception ignored) {}
            forceSamsungPickerRefresh(visibleName, mimeType);
            return uri.toString();
        }

        File cameraDir = Environment.getExternalStoragePublicDirectory(CAMERA_GALLERY_PATH);
        if (!cameraDir.exists() && !cameraDir.mkdirs()) throw new IllegalStateException("gallery-directory-failed");

        File destination = new File(cameraDir, visibleName);
        try (FileOutputStream output = new FileOutputStream(destination, false)) {
            copyFile(source, output);
        }

        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(DOWNLOADS_GALLERY_PATH);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File downloadsCopy = new File(downloadsDir, visibleName);
            try (FileOutputStream output = new FileOutputStream(downloadsCopy, false)) {
                copyFile(source, output);
            }
            MediaScannerConnection.scanFile(getContext(), new String[] { downloadsCopy.getAbsolutePath() }, new String[] { mimeType }, null);
        } catch (Exception ignored) {}

        MediaScannerConnection.scanFile(
            getContext(),
            new String[] { destination.getAbsolutePath() },
            new String[] { mimeType },
            (path, uri) -> {
                if (uri != null) getContext().getContentResolver().notifyChange(uri, null);
            }
        );
        forceSamsungPickerRefresh(visibleName, mimeType);
        return Uri.fromFile(destination).toString();
    }

    private Uri insertGalleryVideo(File source, String fileName, String mimeType, String relativePath, VideoMetadata metadata) throws Exception {
        final long nowMillis = System.currentTimeMillis();
        final long nowSeconds = nowMillis / 1000;
        final String title = fileName.replaceFirst("(?i)\\.[^.]+$", "");
        ContentResolver resolver = getContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, title);
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Video.Media.DATE_ADDED, nowSeconds);
        values.put(MediaStore.Video.Media.DATE_MODIFIED, nowSeconds);
        values.put(MediaStore.Video.Media.DATE_TAKEN, nowMillis);
        values.put(MediaStore.Video.Media.SIZE, metadata.size);
        values.put(MediaStore.Video.Media.DURATION, metadata.durationMs);
        if (metadata.width > 0) values.put(MediaStore.Video.Media.WIDTH, metadata.width);
        if (metadata.height > 0) values.put(MediaStore.Video.Media.HEIGHT, metadata.height);
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) throw new IllegalStateException("gallery-insert-failed");

        try (OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("gallery-output-failed");
            copyFile(source, output);
            output.flush();
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        }

        values.clear();
        values.put(MediaStore.Video.Media.IS_PENDING, 0);
        values.put(MediaStore.Video.Media.DATE_ADDED, nowSeconds);
        values.put(MediaStore.Video.Media.DATE_MODIFIED, nowSeconds);
        values.put(MediaStore.Video.Media.DATE_TAKEN, nowMillis);
        values.put(MediaStore.Video.Media.SIZE, metadata.size);
        values.put(MediaStore.Video.Media.DURATION, metadata.durationMs);
        resolver.update(uri, values, null, null);
        publishMedia(uri, relativePath, fileName, mimeType);
        return uri;
    }

    private Uri insertDownloadCopy(File source, String fileName, String mimeType, VideoMetadata metadata) throws Exception {
        final long nowMillis = System.currentTimeMillis();
        final long nowSeconds = nowMillis / 1000;
        ContentResolver resolver = getContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS_GALLERY_PATH);
        values.put(MediaStore.MediaColumns.SIZE, metadata.size);
        values.put(MediaStore.MediaColumns.DATE_ADDED, nowSeconds);
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, nowSeconds);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) throw new IllegalStateException("downloads-insert-failed");

        try (OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("downloads-output-failed");
            copyFile(source, output);
            output.flush();
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            throw e;
        }

        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        values.put(MediaStore.MediaColumns.SIZE, metadata.size);
        values.put(MediaStore.MediaColumns.DATE_ADDED, nowSeconds);
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, nowSeconds);
        resolver.update(uri, values, null, null);
        publishMedia(uri, DOWNLOADS_GALLERY_PATH, fileName, mimeType);
        return uri;
    }

    private void publishMedia(Uri uri, String relativePath, String fileName, String mimeType) {
        ContentResolver resolver = getContext().getContentResolver();
        resolver.notifyChange(uri, null);
        resolver.notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.notifyChange(MediaStore.Downloads.EXTERNAL_CONTENT_URI, null);
        }
        getContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));

        try {
            File physicalFile = new File(Environment.getExternalStorageDirectory(), relativePath + fileName);
            scanVisibleFile(physicalFile, mimeType);
        } catch (Exception ignored) {}
    }

    private void forceSamsungPickerRefresh(String fileName, String mimeType) {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> refreshAllVisibleCopies(fileName, mimeType), 350);
        handler.postDelayed(() -> refreshAllVisibleCopies(fileName, mimeType), 1400);
    }

    private void refreshAllVisibleCopies(String fileName, String mimeType) {
        ContentResolver resolver = getContext().getContentResolver();
        resolver.notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.notifyChange(MediaStore.Downloads.EXTERNAL_CONTENT_URI, null);
        }

        scanVisibleFile(new File(Environment.getExternalStorageDirectory(), CAMERA_GALLERY_PATH + fileName), mimeType);
        scanVisibleFile(new File(Environment.getExternalStorageDirectory(), DOWNLOADS_GALLERY_PATH + fileName), mimeType);
        scanVisibleFile(new File(Environment.getExternalStorageDirectory(), AETHERX_GALLERY_PATH + "AetherX-" + fileName), mimeType);
    }

    private void scanVisibleFile(File file, String mimeType) {
        MediaScannerConnection.scanFile(
            getContext(),
            new String[] { file.getAbsolutePath() },
            new String[] { mimeType },
            (path, scannedUri) -> {
                if (scannedUri != null) getContext().getContentResolver().notifyChange(scannedUri, null);
            }
        );
    }

    private String getDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return "aetherx-video-importado.mp4";
    }

    private String normalizeMimeType(String mimeType, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (mimeType != null && mimeType.startsWith("video/")) return mimeType;
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".3gp")) return "video/3gpp";
        return DEFAULT_MP4_MIME;
    }

    private String makeFreshGalleryName(String fileName) {
        String normalized = normalizeFileName(fileName);
        String base = normalized.replaceFirst("(?i)\\.[^.]+$", "");
        String ext = ".mp4";
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) ext = normalized.substring(dot);
        return base + "-" + System.currentTimeMillis() + ext;
    }

    private VideoMetadata readVideoMetadata(File file) throws Exception {
        VideoMetadata metadata = new VideoMetadata();
        metadata.size = file.length();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            metadata.durationMs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            metadata.width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            metadata.height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            if (metadata.durationMs <= 0) throw new IllegalStateException("invalid-video-duration");
        } finally {
            retriever.release();
        }
        return metadata;
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void assertPlayableVideo(File file) throws Exception {
        if (!file.exists() || file.length() == 0) throw new IllegalStateException("empty-video-file");
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration == null || duration.length() == 0) throw new IllegalStateException("invalid-video-file");
        } finally {
            retriever.release();
        }
    }

    private void copyFile(File source, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(source)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
