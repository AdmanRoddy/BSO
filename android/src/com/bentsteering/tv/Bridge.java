package com.bentsteering.tv;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** What the home screen page can ask the Fire Stick to do (window.BSOApp in the page). */
final class Bridge {
    private final MainActivity act;
    private final Map<String, String> art = new HashMap<>();

    Bridge(MainActivity act) { this.act = act; }

    /** Installed apps you can open: [{pkg, label}]. */
    @JavascriptInterface
    public String apps() {
        PackageManager pm = act.getPackageManager();
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        String[] cats = { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER };
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("pkg", pkg);
                    o.put("label", String.valueOf(ri.loadLabel(pm)));
                    out.put(o);
                } catch (JSONException ignored) { }
            }
        }
        return out.toString();
    }

    @JavascriptInterface
    public boolean launch(String pkg) {
        PackageManager pm = act.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { act.startActivity(i); return true; } catch (ActivityNotFoundException e) { return false; }
    }

    @JavascriptInterface
    public void openTv(String url) { open(TvActivity.class, url); }

    @JavascriptInterface
    public void openRadio(String url) { open(RadioActivity.class, url); }

    private void open(Class<?> cls, String url) {
        Intent i = new Intent(act, cls).putExtra("url", url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        act.startActivity(i);
    }

    /** Amazon Appstore page for an app that isn't installed. */
    @JavascriptInterface
    public void openStore(String pkg) {
        String[] tries = TextUtils.isEmpty(pkg)
                ? new String[] { "amzn://apps/android?s=apps" }
                : new String[] { "amzn://apps/android?p=" + pkg, "market://details?id=" + pkg };
        for (String u : tries) {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException ignored) { }
        }
    }

    @JavascriptInterface
    public void openSettings() { start(new Intent(Settings.ACTION_SETTINGS)); }

    @JavascriptInterface
    public void openAccessibility() {
        if (!start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) openSettings();
    }

    private boolean start(Intent i) {
        try { act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (ActivityNotFoundException e) { return false; }
    }

    /** Whether the Home-button swap (HomeWatcher) is switched on in Fire TV settings. */
    @JavascriptInterface
    public boolean homeOverride() {
        String on = Settings.Secure.getString(act.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (on == null) return false;
        ComponentName me = new ComponentName(act, HomeWatcher.class);
        for (String s : on.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    /** Go to Amazon's own home screen for a minute (for things only it can do). */
    @JavascriptInterface
    public void fireHome() {
        HomeWatcher.pause(act, 60_000);
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        i.setPackage(HomeWatcher.AMAZON_LAUNCHER);
        if (!start(i)) { i.setPackage(null); start(i); }
    }

    /** Private picture files on the stick: "blueprints" or "badges". */
    @JavascriptInterface
    public String files(String kind) { return new JSONArray(PrivateFiles.list(act, kind)).toString(); }

    @JavascriptInterface
    public String filesFolder(String kind) { return PrivateFiles.folder(act, kind); }

    /** An app's Fire TV banner (or icon) as a data: URL for the All apps grid. */
    @JavascriptInterface
    public String banner(String pkg) {
        synchronized (art) {
            if (art.containsKey(pkg)) return art.get(pkg);
        }
        String url = "";
        try {
            PackageManager pm = act.getPackageManager();
            Drawable d = pm.getApplicationBanner(pkg);
            boolean isBanner = d != null;
            if (d == null) d = pm.getApplicationIcon(pkg);
            int w = isBanner ? 320 : 160, h = isBanner ? 180 : 160;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            if (d instanceof BitmapDrawable || isBanner) { d.setBounds(0, 0, w, h); }
            else { d.setBounds(0, 0, w, h); }
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            url = "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) { }
        synchronized (art) { art.put(pkg, url); }
        return url;
    }

    @JavascriptInterface
    public int versionCode() { return BuildConfigLite.versionCode(act); }

    @JavascriptInterface
    public String versionName() { return BuildConfigLite.versionName(act); }

    /** Download a newer app and hand it to Fire TV's installer. */
    @JavascriptInterface
    public void update(String apkUrl) { Updater.start(act, apkUrl); }

    /** Where to add private pictures from a phone or computer on the same Wi-Fi. */
    @JavascriptInterface
    public String pictureAddress() { return PictureServer.address(); }

    @JavascriptInterface
    public String picturePin() { return PictureServer.start(act).pin; }

    /** Recent screens Fire TV showed (helps tune the Home-button swap). */
    @JavascriptInterface
    public String seen() { return new JSONArray(HomeWatcher.recent()).toString(); }
}
