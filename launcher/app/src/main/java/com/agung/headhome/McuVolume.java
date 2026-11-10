package com.agung.headhome;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;

/** The head unit's MCU volume: read from Settings.System, set through the NWD broadcast. */
final class McuVolume {

    private static final String KEY_VOLUME = "mcu_system_volume";
    private static final String KEY_MAX = "mcu_max_volume";
    private static final String ACTION_SET = "com.nwd.action.ACTION_REQUEST_SET_SYSTEM_VOLUME";
    private static final int DEFAULT_MAX = 40;

    private McuVolume() {
    }

    static int get(Context ctx) {
        return Settings.System.getInt(ctx.getContentResolver(), KEY_VOLUME, 0);
    }

    static int max(Context ctx) {
        int m = Settings.System.getInt(ctx.getContentResolver(), KEY_MAX, DEFAULT_MAX);
        return m > 0 ? m : DEFAULT_MAX;
    }

    static void set(Context ctx, int value) {
        ctx.sendBroadcast(new Intent(ACTION_SET).putExtra("extra_volume", value));
    }

    static ContentObserver observe(Context ctx, Handler handler, Runnable onChange) {
        ContentObserver o = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                onChange.run();
            }
        };
        ctx.getContentResolver().registerContentObserver(Settings.System.getUriFor(KEY_VOLUME), false, o);
        return o;
    }
}
