package com.bentsteering.tv;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Pictures that stay on the Fire Stick and never go on the website (the Botham Design blueprints,
 * crawler photos). They live in the app's own folder, e.g.
 * /sdcard/Android/data/com.bentsteering.tv/files/blueprints/, and the home page reads them
 * as https://bso.local/blueprints/&lt;name&gt;.
 */
final class PrivateFiles {
    static final String HOST = "bso.local";
    private static final String[] KINDS = { "blueprints", "badges" };

    private PrivateFiles() {}

    private static boolean known(String kind) { return Arrays.asList(KINDS).contains(kind); }

    static String folder(Context c, String kind) {
        if (!known(kind)) return "";
        File d = c.getExternalFilesDir(kind);
        if (d == null) return "";
        if (!d.exists()) d.mkdirs();
        return d.getAbsolutePath();
    }

    static List<String> list(Context c, String kind) {
        List<String> out = new ArrayList<>();
        if (!known(kind)) return out;
        File d = c.getExternalFilesDir(kind);
        if (d == null) return out;
        if (!d.exists()) d.mkdirs();
        String[] names = d.list();
        if (names == null) return out;
        Arrays.sort(names);
        for (String n : names) if (mime(n) != null) out.add(n);
        return out;
    }

    static WebResourceResponse serve(Context c, Uri u) {
        if (u == null || !HOST.equals(u.getHost())) return null;
        List<String> seg = u.getPathSegments();
        if (seg.size() != 2 || !known(seg.get(0))) return notFound();
        String name = seg.get(1);
        if (name.contains("/") || name.contains("\\") || name.startsWith(".")) return notFound();
        String type = mime(name);
        File d = c.getExternalFilesDir(seg.get(0));
        if (type == null || d == null) return notFound();
        File f = new File(d, name);
        try {
            return new WebResourceResponse(type, null, new FileInputStream(f));
        } catch (IOException e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", null);
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        return r;
    }

    private static String mime(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        return null;
    }
}
