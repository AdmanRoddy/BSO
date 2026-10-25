package com.bentsteering.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Fire TV won't let another app take over the Home button, so this watches for Amazon's home
 * screen and puts the Bent Steering one in front of it. It also shows the Bent Steering
 * splash when the TV wakes up. Turned on once in Settings › Accessibility.
 */
public class HomeWatcher extends AccessibilityService {
    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final LinkedList<String> RECENT = new LinkedList<>();
    private BroadcastReceiver screenOn;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        screenOn = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) { showHome(true); }
        };
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        // Just switched on (power-on boots straight into this service): show the splash.
        if (SystemClock.elapsedRealtime() < 3 * 60_000) showHome(true);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName(), c = e.getClassName();
        if (p == null) return;
        String pkg = p.toString(), cls = c == null ? "" : c.toString();
        note(pkg + "/" + cls);
        if (!AMAZON_LAUNCHER.equals(pkg)) return;
        if (paused(this)) return;
        // Only Amazon's main home screen, not its other pages (search, profiles, notifications).
        String simple = cls.substring(cls.lastIndexOf('.') + 1);
        if (!simple.contains("Home")) return;
        showHome(false);
    }

    private void showHome(boolean splash) {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if (splash) i.putExtra("splash", true);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (screenOn != null) { try { unregisterReceiver(screenOn); } catch (Exception ignored) { } }
        super.onDestroy();
    }

    /** Leave Amazon's home screen alone for a while (the "Fire TV home" button). */
    static void pause(Context c, long ms) {
        c.getSharedPreferences("home", MODE_PRIVATE).edit().putLong("pauseUntil", System.currentTimeMillis() + ms).apply();
    }

    private static boolean paused(Context c) {
        return System.currentTimeMillis() < c.getSharedPreferences("home", MODE_PRIVATE).getLong("pauseUntil", 0);
    }

    private static void note(String s) {
        synchronized (RECENT) {
            if (!RECENT.isEmpty() && RECENT.getLast().equals(s)) return;
            RECENT.add(s);
            while (RECENT.size() > 20) RECENT.removeFirst();
        }
    }

    static List<String> recent() {
        synchronized (RECENT) { return new ArrayList<>(RECENT); }
    }
}
