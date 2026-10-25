package com.bentsteering.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Tidies pictures as they arrive on the stick. Blueprints come in as screenshots (light lines on a
 * dark gray or white background): the background becomes transparent and the lines white, then the
 * picture is trimmed to the drawing, so it sits cleanly on the home screen. Colorful pictures
 * (crawler photos and logos) are moved to the crawler-photo folder untouched.
 */
final class Pictures {
    private static final int MAX_SIDE = 2000;

    private Pictures() {}

    /** Returns the folder the picture ended up in ("blueprints" or "badges"). */
    static String ingest(Context c, String kind, File f) {
        try {
            if (!"blueprints".equals(kind)) return kind;
            Bitmap b = decode(f);
            if (b == null) return kind;
            if (colorful(b)) {
                b.recycle();
                File dir = new File(PrivateFiles.folder(c, "badges"));
                File to = new File(dir, f.getName());
                if (to.exists()) to.delete();
                if (f.renameTo(to)) return "badges";
                return kind;
            }
            Bitmap clean = lineArt(b);
            b.recycle();
            if (clean == null) return kind;
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            File out = new File(f.getParentFile(), (dot > 0 ? name.substring(0, dot) : name) + ".png");
            File tmp = new File(f.getParentFile(), "." + out.getName() + ".part");
            try (FileOutputStream o = new FileOutputStream(tmp)) { clean.compress(Bitmap.CompressFormat.PNG, 100, o); }
            clean.recycle();
            if (!out.equals(f)) f.delete();
            if (out.exists()) out.delete();
            tmp.renameTo(out);
        } catch (Throwable ignored) { /* keep the picture as it came */ }
        return kind;
    }

    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > MAX_SIDE) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** More than 4% clearly colored pixels: a photo or logo, not a blueprint. */
    private static boolean colorful(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), step = Math.max(1, Math.min(w, h) / 160), n = 0, hits = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < h; y += step) for (int x = 0; x < w; x += step) {
            Color.colorToHSV(b.getPixel(x, y), hsv);
            n++;
            if (hsv[1] > 0.35f && hsv[2] > 0.25f) hits++;
        }
        return n > 0 && hits * 100 > n * 4;
    }

    private static Bitmap lineArt(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        // background = the typical colour along the edges
        int m = Math.max(1, Math.min(w, h) / 200), k = 0;
        int[] edge = new int[2 * (w + h) / m + 8];
        for (int x = 0; x < w && k < edge.length - 2; x += m) { edge[k++] = lum(px[x]); edge[k++] = lum(px[(h - 1) * w + x]); }
        for (int y = 0; y < h && k < edge.length - 2; y += m) { edge[k++] = lum(px[y * w]); edge[k++] = lum(px[y * w + w - 1]); }
        int[] e = Arrays.copyOf(edge, k);
        Arrays.sort(e);
        int bg = e[k / 2];
        boolean darkBg = bg < 128;
        int span = Math.max(24, darkBg ? 255 - bg : bg);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int i = 0; i < px.length; i++) {
            int l = lum(px[i]);
            int a = darkBg ? (l - bg) * 255 / span : (bg - l) * 255 / span;
            a = a < 12 ? 0 : Math.min(255, (int) (a * 1.15f));
            px[i] = (a << 24) | 0xFFFFFF;                  // white lines, background see-through
            if (a > 40) {
                int x = i % w, y = i / w;
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) return null;
        int pad = Math.max(4, Math.min(w, h) / 60);
        minX = Math.max(0, minX - pad); minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad); maxY = Math.min(h - 1, maxY + pad);
        Bitmap full = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        Bitmap cut = Bitmap.createBitmap(full, minX, minY, maxX - minX + 1, maxY - minY + 1);
        if (cut != full) full.recycle();
        return cut;
    }

    private static int lum(int c) {
        return (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
    }

    static boolean isPicture(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".gif");
    }
}
