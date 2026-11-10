package com.agung.headhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

final class Launch {

    static final ComponentName NWD_MUSIC = nwd("com.nwd.android.music.ui");
    static final ComponentName ANDROID_SETTINGS = new ComponentName("com.android.settings", "com.android.settings.Settings");
    static final ComponentName NWD_PHONE = nwd("com.nwd.android.phone");
    static final ComponentName NWD_DSP = nwd("com.nwd.audioset");

    private Launch() {
    }

    private static ComponentName nwd(String pkg) {
        return new ComponentName(pkg, pkg + ".home_horizontalActivity");
    }

    /** Starts an activity in a new task. Shows a toast and returns false if it cannot be started. */
    static boolean start(Context ctx, ComponentName cn) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(ctx, R.string.app_not_found, Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
