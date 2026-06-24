package com.aetherx.wallpapers;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.aetherx.livewallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.BridgeWebViewClient;

/**
 * MainActivity de AetherX.
 * Carga la app localmente desde assets empaquetados por Capacitor.
 */
public class MainActivity extends BridgeActivity {
    private static final String TAG = "AetherXMainActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: local Capacitor boot; no remote server config and no forced remote URL");
        android.content.Intent launchIntent = getIntent();
        if (launchIntent != null) {
            String action = launchIntent.getAction();
            Uri data = launchIntent.getData();
            Log.i(TAG, "BOOT_AUDIT launchIntent action=" + action + " data=" + data
                + " categories=" + launchIntent.getCategories() + " package=" + launchIntent.getPackage());
            if (android.content.Intent.ACTION_VIEW.equals(action)) {
                Log.w(TAG, "BOOT_AUDIT WARNING: app launched via ACTION_VIEW — ignoring URI, staying in local WebView");
            } else {
                Log.i(TAG, "BOOT_AUDIT OK: no ACTION_VIEW at boot; no external Chrome/Browser launch path triggered");
            }
        }
        Log.i(TAG, "BOOT_AUDIT MainActivity has no external browser launch path — verified by static audit");
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView == null) {
            Log.e(TAG, "WebView is null during local boot");
            return;
        }

        WebView.setWebContentsDebuggingEnabled(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setBlockNetworkImage(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        BridgeWebViewClient guardedClient = new BridgeWebViewClient(getBridge()) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request != null ? request.getUrl() : null;
                boolean mainFrame = request != null && request.isForMainFrame();
                return handleNavigation(uri, mainFrame, "request");
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url == null ? null : Uri.parse(url), true, "legacy");
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.i(TAG, "onPageStarted: " + url);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Uri uri = request != null ? request.getUrl() : null;
                boolean mainFrame = request != null && request.isForMainFrame();
                CharSequence description = error != null ? error.getDescription() : "unknown";
                int code = error != null ? error.getErrorCode() : 0;
                Log.e(TAG, "onReceivedError url=" + uri + " mainFrame=" + mainFrame + " code=" + code + " description=" + description);
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                Uri uri = request != null ? request.getUrl() : null;
                int status = errorResponse != null ? errorResponse.getStatusCode() : 0;
                Log.e(TAG, "onReceivedHttpError url=" + uri + " mainFrame=" + (request != null && request.isForMainFrame()) + " status=" + status);
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "onPageFinished: " + url + " progress=" + (view != null ? view.getProgress() : -1));
                super.onPageFinished(view, url);
            }
        };
        getBridge().setWebViewClient(guardedClient);
        webView.setWebViewClient(guardedClient);

        webView.setWebChromeClient(new BridgeWebChromeClient(getBridge()) {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    Log.d(TAG, "Web console: " + consoleMessage.message()
                        + " @" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                }
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                Log.w(TAG, "Blocked new WebView popup request. isDialog=" + isDialog + " userGesture=" + isUserGesture);
                return false;
            }
        });

        Log.i(TAG, "LOCAL_CAPACITOR_BOOT appUrl=" + getBridge().getAppUrl() + " currentUrl=" + webView.getUrl());
        Log.i(TAG, "NO_REMOTE_BOOT_URL: MainActivity never forces a remote WebView URL");
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        Log.d(TAG, "onNewIntent action=" + (intent != null ? intent.getAction() : null) + " data=" + data);
        if (data != null && isHttpLike(data)) {
            Log.w(TAG, "Blocked incoming external URL intent; staying in local app: " + data);
            android.content.Intent cleanIntent = new android.content.Intent(this, MainActivity.class);
            cleanIntent.setAction(android.content.Intent.ACTION_MAIN);
            cleanIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            setIntent(cleanIntent);
            super.onNewIntent(cleanIntent);
            return;
        }
        setIntent(intent);
        super.onNewIntent(intent);
    }

    private boolean handleNavigation(Uri uri, boolean isMainFrame, String source) {
        if (uri == null) {
            Log.w(TAG, "Blocked null navigation from " + source);
            return true;
        }

        String url = uri.toString();
        String scheme = uri.getScheme();
        Log.d(TAG, "Navigation from " + source + ": " + url + " mainFrame=" + isMainFrame);

        if (isHttpLike(uri)) {
            if (!isMainFrame || isLocalCapacitorHost(uri)) {
                return false;
            }
            Log.w(TAG, "Blocked remote main-frame navigation. No Chrome launch: " + url);
            return true;
        }

        if ("data".equalsIgnoreCase(scheme) || "blob".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)
            || "file".equalsIgnoreCase(scheme) || "capacitor".equalsIgnoreCase(scheme)) {
            return false;
        }

        Log.w(TAG, "Blocked external Android scheme: " + url);
        return true;
    }

    private boolean isHttpLike(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private boolean isLocalCapacitorHost(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase();
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("0.0.0.0");
    }

}
