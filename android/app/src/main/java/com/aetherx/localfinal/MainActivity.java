package com.aetherx.localfinal;

import android.os.Bundle;

import com.aetherx.localfinal.wallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
