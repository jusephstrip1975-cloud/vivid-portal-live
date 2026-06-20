package com.aetherx.livewallpaper;

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

@CapacitorPlugin(name = "AetherXLiveWallpaper")
public class AetherXLiveWallpaperPlugin extends Plugin {
    static final String VIDEO_FILE = "aetherx-live-wallpaper.mp4";

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

            JSObject result = new JSObject();
            result.put("path", file.getAbsolutePath());
            result.put("bytes", bytes.length);
            call.resolve(result);
        } catch (Exception e) {
            call.reject("video-save-failed", e);
        }
    }

    @PluginMethod
    public void openPicker(PluginCall call) {
        try {
            ComponentName service = new ComponentName(getContext(), AetherXVideoWallpaperService.class);
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, service);
            } else {
                intent = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
            }
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
}