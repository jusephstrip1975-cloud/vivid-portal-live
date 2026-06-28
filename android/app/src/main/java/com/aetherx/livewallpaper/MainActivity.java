package com.aetherx.livewallpaper;

import android.os.Bundle;

import com.aetherx.livewallpaper.wallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
