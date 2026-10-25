package com.bentsteering.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * The Bent Steering home screen. The page itself lives on the website so its look can change
 * without reinstalling; a copy ships inside the app for when the internet is down.
 */
public class MainActivity extends Activity {
    static final String HOME_URL = "https://admanroddy.github.io/BSO/home/";
    static final String LOCAL_URL = "file:///android_asset/home/index.html";

    private WebView web;
    private boolean usingLocal = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Web.fullScreen(this, false);
        web = Web.make(this);
        web.addJavascriptInterface(new Bridge(this), "BSOApp");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                WebResourceResponse res = PrivateFiles.serve(MainActivity.this, r.getUrl());
                return res != null ? res : super.shouldInterceptRequest(v, r);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView v, int code, String desc, String url) {
                if (url != null && url.startsWith(HOME_URL)) fallBack();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) fallBack();
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse e) {
                if (r.isForMainFrame() && e.getStatusCode() >= 400) fallBack();
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap icon) {
                super.onPageStarted(v, url, icon);
            }
        });
        setContentView(web);
        web.loadUrl(HOME_URL + "?app=" + BuildConfigLite.versionCode(this));
        web.requestFocus();
    }

    private void fallBack() {
        if (usingLocal) return;
        usingLocal = true;
        runOnUiThread(new Runnable() { @Override public void run() { web.loadUrl(LOCAL_URL); } });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("splash", false)) js("window.bsoSplash&&bsoSplash()");
        web.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Web.fullScreen(this, false);
        js("window.bsoResume&&bsoResume()");
        // try the website again next time if we fell back while offline
        if (usingLocal) { usingLocal = false; web.loadUrl(HOME_URL + "?app=" + BuildConfigLite.versionCode(this) + "&nosplash=1"); }
    }

    /** This is the home screen: Back closes whatever is open on it, and never leaves. */
    @Override
    public void onBackPressed() {
        js("window.bsoBack&&bsoBack()");
    }

    void js(final String code) {
        runOnUiThread(new Runnable() {
            @Override public void run() { if (web != null) web.evaluateJavascript(code, null); }
        });
    }
}
