package com.bentsteering.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Shared set-up for the app's full-screen web views. */
final class Web {
    /** Added to the browser's user agent so BSO TV and J R-dio know they're inside the app. */
    static final String UA_TAG = " BSOTV/1.0";

    private Web() {}

    @SuppressLint("SetJavaScriptEnabled")
    static WebView make(Activity act) {
        WebView w = new WebView(act);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);     // channels and stations start without a tap
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= 21) {
            // lets J R-dio play stations that only stream over plain http://
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        s.setUserAgentString(s.getUserAgentString() + UA_TAG);
        w.setBackgroundColor(0xFF070D1F);
        w.setFocusable(true);
        w.setFocusableInTouchMode(true);
        w.setWebChromeClient(new WebChromeClient());
        return w;
    }

    static void fullScreen(Activity act, boolean keepScreenOn) {
        int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        if (keepScreenOn) flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        act.getWindow().addFlags(flags);
        act.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
