package com.aetherx.wallpapers;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.aetherx.livewallpaper.AetherXLiveWallpaperPlugin;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "AetherXMainActivity";
    private static final String AETHERX_URL = "https://aetherx.org";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate: registering native live wallpaper plugin before bridge load");
        registerPlugin(AetherXLiveWallpaperPlugin.class);
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: bridge created. intent=" + describeIntent(getIntent()));

        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView == null) {
            Log.e(TAG, "onCreate: WebView is null; cannot load " + AETHERX_URL);
            return;
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setBlockNetworkImage(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        BridgeWebViewClient guardedClient = new BridgeWebViewClient(getBridge()) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return handleNavigation(view, uri, request != null && request.isForMainFrame(), "request");
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(view, url == null ? null : Uri.parse(url), true, "legacy");
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d(TAG, "onPageStarted: " + url);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url + " progress=" + (view != null ? view.getProgress() : -1));
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Log.e(TAG, "onReceivedError: url=" + (request != null ? request.getUrl() : null)
                    + " main=" + (request != null && request.isForMainFrame())
                    + " code=" + (error != null ? error.getErrorCode() : 0)
                    + " desc=" + (error != null ? error.getDescription() : null));
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                Log.e(TAG, "onReceivedHttpError: url=" + (request != null ? request.getUrl() : null)
                    + " main=" + (request != null && request.isForMainFrame())
                    + " status=" + (errorResponse != null ? errorResponse.getStatusCode() : 0));
                super.onReceivedHttpError(view, request, errorResponse);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, "onRenderProcessGone: didCrash=" + (detail != null && detail.didCrash()));
                return super.onRenderProcessGone(view, detail);
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
                Log.w(TAG, "Blocked new WebView/window.open target. isDialog=" + isDialog + " userGesture=" + isUserGesture);
                return false;
            }
        });

        String currentUrl = webView.getUrl();
        String bridgeUrl = getBridge().getAppUrl();
        Log.d(TAG, "Configured bridge appUrl=" + bridgeUrl + " currentWebViewUrl=" + currentUrl);
        if (currentUrl == null || currentUrl.length() == 0 || currentUrl.startsWith("capacitor://") || currentUrl.startsWith("http://")) {
            Log.d(TAG, "Forcing initial WebView load: " + AETHERX_URL);
            webView.post(() -> webView.loadUrl(AETHERX_URL));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent: " + describeIntent(intent));
        WebView webView = getBridge() != null ? getBridge().getWebView() : null;
        if (webView != null) {
            Uri data = intent != null ? intent.getData() : null;
            if (data != null && ("http".equalsIgnoreCase(data.getScheme()) || "https".equalsIgnoreCase(data.getScheme()))) {
                Log.d(TAG, "Loading incoming http(s) intent inside WebView instead of external browser: " + data);
                webView.loadUrl(data.toString());
            }
        }
    }

    private boolean handleNavigation(WebView view, Uri uri, boolean isMainFrame, String source) {
        if (uri == null) {
            Log.w(TAG, "Navigation blocked from " + source + ": null uri");
            return true;
        }

        String url = uri.toString();
        String scheme = uri.getScheme();
        Log.d(TAG, "Navigation request from " + source + ": " + url + " mainFrame=" + isMainFrame);

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String current = view != null ? view.getUrl() : null;
            if (view != null && (current == null || !current.equals(url))) {
                Log.d(TAG, "Forcing http(s) navigation inside WebView: " + url);
                view.loadUrl(url);
                return true;
            }
            Log.d(TAG, "Allowing current http(s) navigation inside WebView: " + url);
            return false;
        }

        if ("data".equalsIgnoreCase(scheme) || "blob".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)
            || "file".equalsIgnoreCase(scheme) || "capacitor".equalsIgnoreCase(scheme)) {
            return false;
        }

        Log.w(TAG, "Blocked external Android intent/scheme: " + url);
        return true;
    }

    private String describeIntent(Intent intent) {
        if (intent == null) return "null";
        return "action=" + intent.getAction() + " data=" + intent.getData() + " categories=" + intent.getCategories();
    }
}
