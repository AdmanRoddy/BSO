package com.bentsteering.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Full-screen page for BSO TV or J R-dio. */
public abstract class WebActivity extends Activity {
    protected WebView web;
    private String loaded;

    /** J R-dio keeps playing after you go back home; BSO TV stops. */
    protected abstract boolean playsInBackground();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Web.fullScreen(this, true);
        web = Web.make(this);
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        load(getIntent());
    }

    private void load(Intent i) {
        String url = i == null ? null : i.getStringExtra("url");
        if (url != null && !url.equals(loaded)) { loaded = url; web.loadUrl(url); }
        web.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        load(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Web.fullScreen(this, true);
        web.requestFocus();
    }

    /** Back first lets the page close its own menu (BSO's guide, J R-dio's categories). */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.bsoBack&&window.bsoBack())?'1':'0'", new android.webkit.ValueCallback<String>() {
            @Override public void onReceiveValue(String v) { if (v == null || !v.contains("1")) leave(); }
        });
    }

    protected void leave() {
        if (playsInBackground()) moveTaskToBack(true);
        else finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!playsInBackground() && !isFinishing()) finish();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
