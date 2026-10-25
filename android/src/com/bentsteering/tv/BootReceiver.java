package com.bentsteering.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Power-on: show the Bent Steering splash and home screen (older Fire OS allows this directly). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Intent home = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("splash", true);
        try { c.startActivity(home); } catch (Exception ignored) { }
    }
}
