package com.aetherx.livewallpaper;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.aetherx.livewallpaper.wallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "AetherXMain";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);
        handleWallpaperIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWallpaperIntent(intent);
    }

    private void handleWallpaperIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"aetherx".equals(data.getScheme()) || !"wallpaper".equals(data.getHost())) return;

        String url = data.getQueryParameter("url");
        String fileName = data.getQueryParameter("fileName");
        String target = data.getQueryParameter("target");
        if (url == null || url.trim().isEmpty()) {
            Log.w(TAG, "Wallpaper intent missing url: " + data);
            return;
        }
        AetherXLiveWallpaperPlugin.applyWallpaperFromUrl(this, url, fileName, target);
    }
}
