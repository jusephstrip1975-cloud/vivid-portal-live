package com.aetherx.livewallpaper;

import android.os.Bundle;
import android.util.Log;

import com.aetherx.livewallpaper.wallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "AetherXLiveWP";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "APP_BUILD_VERSION=" + BuildConfig.VERSION_NAME
            + " build=" + BuildConfig.AETHERX_BUILD_VERSION
            + " versionCode=" + BuildConfig.VERSION_CODE
            + " versionName=" + BuildConfig.VERSION_NAME);
        Log.i(TAG, BuildConfig.AETHERX_BUILD_MARKER);
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
