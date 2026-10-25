package com.bentsteering.tv;

import android.os.Bundle;

/** BSO TV. Turns the radio off when it starts so the two don't play over each other. */
public class TvActivity extends WebActivity {
    @Override
    protected boolean playsInBackground() { return false; }

    @Override
    protected void onCreate(Bundle state) {
        RadioActivity.stopRadio();
        super.onCreate(state);
    }
}
