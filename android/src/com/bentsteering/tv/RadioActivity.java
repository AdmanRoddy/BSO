package com.bentsteering.tv;

import android.os.Bundle;
import java.lang.ref.WeakReference;

/** J R-dio. Keeps playing in the background while you use the rest of the TV. */
public class RadioActivity extends WebActivity {
    private static WeakReference<RadioActivity> current = new WeakReference<>(null);

    @Override
    protected boolean playsInBackground() { return true; }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = new WeakReference<>(this);
    }

    static void stopRadio() {
        RadioActivity r = current.get();
        if (r != null && !r.isFinishing()) r.finish();
    }
}
