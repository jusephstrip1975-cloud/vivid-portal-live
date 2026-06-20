package com.aetherx.livewallpaper;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "AetherXLiveWallpaper")
public class AetherXLiveWallpaperPlugin extends Plugin {
    static final String VIDEO_FILE = "aetherx-live-wallpaper.mp4";

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

                resolveSaved(call, file, total);
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

            resolveSaved(call, file, bytes.length);
        } catch (Exception e) {
            call.reject("video-save-failed", e);
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

    private void resolveSaved(PluginCall call, File file, int bytes) {
        JSObject result = new JSObject();
        result.put("path", file.getAbsolutePath());
        result.put("bytes", bytes);
        call.resolve(result);
    }
}