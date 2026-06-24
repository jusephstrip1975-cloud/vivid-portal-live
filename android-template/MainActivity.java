package com.aetherx.wallpapers;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import com.aetherx.livewallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;

/**
 * MainActivity de AetherX.
 * Carga la app localmente desde assets (dist/client).
 */
public class MainActivity extends BridgeActivity {
    private static final String TAG = "AetherXMainActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: registering native live wallpaper plugin");
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);
        
        // El WebView de Capacitor cargará automáticamente el contenido de dist/client
        // definido en capacitor.config.ts (webDir).
        
        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            // Habilitar depuración para facilitar inspección local
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }
}
