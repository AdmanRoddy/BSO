package com.bentsteering.tv;

import android.content.Context;
import android.content.pm.PackageInfo;

/** The installed app's version, read from the package (set by the build). */
final class BuildConfigLite {
    private BuildConfigLite() {}

    @SuppressWarnings("deprecation")
    static int versionCode(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.versionCode;
        } catch (Exception e) { return 0; }
    }

    static String versionName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }
}
