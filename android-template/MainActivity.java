package com.aetherx.wallpapers;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;
import com.aetherx.livewallpaper.AetherXLiveWallpaperPlugin;

/**
 * MainActivity de AetherX.
 *
 * IMPORTANTE: Este archivo se copia automáticamente sobre el MainActivity.java
 * que genera Capacitor en `android/app/src/main/java/com/aetherx/wallpapers/`
 * cuando ejecutas `scripts/build-android.sh`.
 *
 * Qué hace distinto al MainActivity por defecto:
 *  - Registra el plugin nativo AetherXLiveWallpaper ANTES de super.onCreate
 *    (requerido por Capacitor 6+).
 *  - Ajusta el WebView para que la web de aetherx.org se vea correctamente:
 *      · JavaScript, DOM storage y bases de datos habilitados.
 *      · Mixed content bloqueado (todo HTTPS).
 *      · Cache estándar, sin viewport override.
 *      · UserAgent estándar de Android WebView (el sitio detecta móvil).
 *
 *  - NO sobreescribe shouldOverrideUrlLoading: Capacitor ya gestiona las
 *    redirecciones contra la lista `server.allowNavigation` definida en
 *    capacitor.config.ts. Cualquier dominio que esté allí se queda dentro
 *    del WebView; cualquier otro se abre en el navegador externo.
 */
public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Registrar plugin nativo antes de inicializar el bridge.
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        if (webView != null) {
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
        }
    }
}
