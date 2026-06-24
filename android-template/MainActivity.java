package com.aetherx.wallpapers;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.aetherx.livewallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.BridgeWebViewClient;
import com.getcapacitor.ServerPath;

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
                setIntent(createCleanLauncherIntent());
            } else {
                Log.i(TAG, "BOOT_AUDIT OK: no ACTION_VIEW at boot; no external Chrome/Browser launch path triggered");
            }
        }
        Log.i(TAG, "BOOT_AUDIT MainActivity has no external browser launch path — forcing assets/public before Bridge load");
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        bridgeBuilder.setServerPath(new ServerPath(ServerPath.PathType.ASSET_PATH, "public"));
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
                if (mainFrame) {
                    showNativeBootError(view, "WebView main-frame error " + code + ": " + description + "\nURL: " + uri);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                Uri uri = request != null ? request.getUrl() : null;
                int status = errorResponse != null ? errorResponse.getStatusCode() : 0;
                Log.e(TAG, "onReceivedHttpError url=" + uri + " mainFrame=" + (request != null && request.isForMainFrame()) + " status=" + status);
                super.onReceivedHttpError(view, request, errorResponse);
                if (request != null && request.isForMainFrame()) {
                    showNativeBootError(view, "WebView main-frame HTTP error " + status + "\nURL: " + uri);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, "WebView render process gone. didCrash=" + (detail != null && detail.didCrash()));
                showNativeBootError(view, "WebView render process gone. didCrash=" + (detail != null && detail.didCrash()));
                return true;
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
            android.content.Intent cleanIntent = createCleanLauncherIntent();
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

    private android.content.Intent createCleanLauncherIntent() {
        android.content.Intent cleanIntent = new android.content.Intent(this, MainActivity.class);
        cleanIntent.setAction(android.content.Intent.ACTION_MAIN);
        cleanIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
        cleanIntent.setPackage(getPackageName());
        cleanIntent.setData(null);
        return cleanIntent;
    }

    private void showNativeBootError(WebView view, String message) {
        if (view == null) return;
        String safeMessage = message == null ? "Error desconocido" : message
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        String html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>html,body{margin:0;min-height:100%;background:#02040a;color:#f8fafc;font-family:system-ui,-apple-system,Segoe UI,sans-serif}"
            + "main{min-height:100vh;display:grid;place-items:center;padding:24px;box-sizing:border-box;background:radial-gradient(circle at 30% 20%,rgba(14,165,233,.24),transparent 38%),#02040a}"
            + "section{max-width:560px}h1{font-size:28px;margin:0 0 8px;letter-spacing:.08em}p{color:rgba(248,250,252,.7)}pre{white-space:pre-wrap;color:#fecaca;background:rgba(127,29,29,.28);border:1px solid rgba(239,68,68,.55);border-radius:14px;padding:14px;font:12px/1.45 monospace}</style></head>"
            + "<body><main><section><h1>AetherX</h1><p>AetherX cargando local</p><pre>" + safeMessage + "</pre></section></main></body></html>";
        view.post(() -> view.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null));
    }

}
