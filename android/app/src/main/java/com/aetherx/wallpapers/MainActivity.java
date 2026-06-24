package com.aetherx.wallpapers;

import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.aetherx.livewallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    private static final String AETHERX_URL = "https://aetherx.org";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView == null) return;

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
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        // No abrir Chrome externo desde Capacitor: http/https siempre se resuelve
        // dentro del WebView; esquemas Android externos (intent:, market:, etc.) se bloquean.
        getBridge().setWebViewClient(new BridgeWebViewClient(getBridge()) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri != null ? uri.getScheme() : null;
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                return !("data".equalsIgnoreCase(scheme) || "blob".equalsIgnoreCase(scheme));
            }
        });

        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.length() == 0 || currentUrl.startsWith("capacitor://")) {
            webView.loadUrl(AETHERX_URL);
        }
    }
}
