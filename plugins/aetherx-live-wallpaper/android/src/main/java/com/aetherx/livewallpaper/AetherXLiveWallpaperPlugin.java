package com.aetherx.livewallpaper;

import android.app.WallpaperManager;
import android.app.WallpaperInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "AetherXLiveWallpaper")
public class AetherXLiveWallpaperPlugin extends Plugin {
    static final String VIDEO_FILE = "aetherx-live-wallpaper.mp4";
    private Uri lastGalleryVideoUri;

    @PluginMethod
    public void saveVideoFromUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.length() == 0) {
            call.reject("missing-video-url");
            return;
        }

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

                String fileName = sanitizeVideoFileName(call.getString("fileName"));
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

                Uri galleryUri = tryCopyVideoToGallery(file, fileName);
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
            String fileName = sanitizeVideoFileName(call.getString("fileName"));
            File file = new File(getContext().getFilesDir(), VIDEO_FILE);
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(bytes);
            }

            Uri galleryUri = tryCopyVideoToGallery(file, fileName);
            resolveSaved(call, file, bytes.length, galleryUri);
        } catch (Exception e) {
            call.reject("video-save-failed", e);
        }
    }

    private void resolveSaved(PluginCall call, File file, int bytes, Uri galleryUri) {
        JSObject result = new JSObject();
        result.put("path", file.getAbsolutePath());
        result.put("bytes", bytes);
        if (galleryUri != null) result.put("galleryUri", galleryUri.toString());
        call.resolve(result);
    }

    private String sanitizeVideoFileName(String fileName) {
        String fallback = "aetherx-live-wallpaper-" + System.currentTimeMillis() + ".mp4";
        if (fileName == null || fileName.trim().length() == 0) return fallback;
        String safe = fileName.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (!safe.toLowerCase().endsWith(".mp4")) safe = safe + ".mp4";
        return safe;
    }

    private Uri tryCopyVideoToGallery(File source, String fileName) {
        try {
            return copyVideoToGallery(source, fileName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Uri copyVideoToGallery(File source, String fileName) throws Exception {
        ContentResolver resolver = getContext().getContentResolver();
        Uri videoUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Video.Media.TITLE, fileName.replace(".mp4", ""));
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AetherX");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);

            Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            videoUri = resolver.insert(collection, values);
            if (videoUri == null) throw new IllegalStateException("gallery-video-uri-null");

            try (InputStream input = new java.io.FileInputStream(source); OutputStream output = resolver.openOutputStream(videoUri, "w")) {
                if (output == null) throw new IllegalStateException("gallery-video-output-null");
                copy(input, output);
            }

            ContentValues published = new ContentValues();
            published.put(MediaStore.Video.Media.IS_PENDING, 0);
            resolver.update(videoUri, published, null, null);
        } else {
            File moviesDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AetherX");
            if (!moviesDir.exists() && !moviesDir.mkdirs()) throw new IllegalStateException("gallery-dir-create-failed");
            File target = new File(moviesDir, fileName);
            try (InputStream input = new java.io.FileInputStream(source); FileOutputStream output = new FileOutputStream(target, false)) {
                copy(input, output);
            }
            MediaScannerConnection.scanFile(getContext(), new String[] { target.getAbsolutePath() }, new String[] { "video/mp4" }, null);
            videoUri = Uri.fromFile(target);
        }

        lastGalleryVideoUri = videoUri;
        return videoUri;
    }

    private void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    @PluginMethod
    public void openPicker(PluginCall call) {
        try {
            ComponentName service = new ComponentName(getContext(), AetherXVideoWallpaperService.class);
            openLiveWallpaperPreview(service);

            JSObject result = new JSObject();
            result.put("opened", true);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("live-wallpaper-picker-failed", e);
        }
    }

    private void openLiveWallpaperPreview(ComponentName service) throws Exception {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, service);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                getActivity().startActivity(intent);
                return;
            } catch (Exception ignored) {
                // Some Android skins do not expose the direct preview action.
                // Fall back to the live wallpaper chooser, never to the image gallery.
            }
        }

        intent = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getActivity().startActivity(intent);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
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

                manager.setWallpaperComponent(service);

                WallpaperInfo info = manager.getWallpaperInfo();
                boolean verified = info != null
                    && getContext().getPackageName().equals(info.getPackageName())
                    && AetherXVideoWallpaperService.class.getName().equals(info.getServiceName());

                JSObject result = new JSObject();
                result.put("applied", true);
                result.put("verified", verified);
                call.resolve(result);
                return;
            }

            call.reject("live-wallpaper-home-apply-unsupported");
        } catch (Exception e) {
            call.reject("live-wallpaper-home-apply-failed", e);
        }
    }

    @PluginMethod
    public void openGalleryVideo(PluginCall call) {
        if (lastGalleryVideoUri == null) {
            call.reject("missing-gallery-video");
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(lastGalleryVideoUri, "video/mp4");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(intent);

            JSObject result = new JSObject();
            result.put("opened", true);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("gallery-video-open-failed", e);
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
}