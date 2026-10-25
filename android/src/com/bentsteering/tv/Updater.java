package com.bentsteering.tv;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads a newer Bent Steering TV and hands it to Fire TV's installer. */
final class Updater {
    private Updater() {}

    static void start(final Activity act, final String apkUrl) {
        new Thread(new Runnable() { @Override public void run() {
            try {
                PackageInstaller installer = act.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int id = installer.createSession(params);
                PackageInstaller.Session session = installer.openSession(id);
                HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(20_000);
                c.setReadTimeout(60_000);
                try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("bso-tv.apk", 0, -1)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                }
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= 0x02000000;   // FLAG_MUTABLE: the installer adds its result
                PendingIntent done = PendingIntent.getBroadcast(act, id, new Intent(act, InstallReceiver.class), flags);
                session.commit(done.getIntentSender());
                session.close();
            } catch (final Exception e) {
                act.runOnUiThread(new Runnable() { @Override public void run() {
                    Toast.makeText(act, "Update didn't download: " + e.getMessage(), Toast.LENGTH_LONG).show();
                } });
            }
        } }).start();
    }
}
