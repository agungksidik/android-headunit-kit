package com.agung.headhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** The restart confirm dialog, shared by the Home button and the settings screen. */
final class RestartDialog {

    /** Latest GPS speed in km/h, 0 without a fix. Written by MainActivity. */
    static volatile int currentSpeedKmh;

    private RestartDialog() {
    }

    static void show(Activity activity) {
        final int speed = currentSpeedKmh;
        new AlertDialog.Builder(activity, R.style.Theme_HeadHome_Dialog)
                .setTitle(R.string.restart_title)
                .setMessage(speed > 0
                        ? activity.getString(R.string.restart_moving, speed)
                        : activity.getString(R.string.restart_body))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restart, (d, w) -> restartUnit(activity.getApplicationContext()))
                .create()
                .show();
    }

    private static void restartUnit(Context app) {
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                LocalAdb.reboot(app.getFilesDir());
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                handler.post(() -> Toast.makeText(app,
                        app.getString(R.string.restart_failed, reason), Toast.LENGTH_LONG).show());
            }
        }, "adb-reboot").start();
    }
}
