package com.agung.headhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the process after boot so Custom Sound can send its saved setting to the DSP chip. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SoundEngine.get(context).restoreOnce();
        }
    }
}
