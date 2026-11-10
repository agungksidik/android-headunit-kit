package com.agung.headhome;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

/** MCU volume level bar with minus and plus buttons; holding a button repeats every 150 ms. */
final class VolumeControl {

    private static final long REPEAT_START_MS = 400;
    private static final long REPEAT_MS = 150;

    private final Context ctx;
    private final Handler handler;
    private final ProgressBar bar;
    private final TextView value;
    private int volume;
    private int max = 40;
    private ContentObserver observer;

    VolumeControl(Context ctx, Handler handler, ProgressBar bar, TextView value, View minus, View plus) {
        this.ctx = ctx;
        this.handler = handler;
        this.bar = bar;
        this.value = value;
        holdToRepeat(minus, -1);
        holdToRepeat(plus, 1);
        read();
    }

    void start() {
        observer = McuVolume.observe(ctx, handler, this::read);
        read();
    }

    void stop() {
        if (observer != null) {
            ctx.getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
    }

    private void read() {
        max = McuVolume.max(ctx);
        volume = McuVolume.get(ctx);
        show();
    }

    private void show() {
        bar.setMax(max);
        bar.setProgress(volume);
        value.setText(String.valueOf(volume));
    }

    private void step(int delta) {
        int v = Math.max(0, Math.min(max, volume + delta));
        if (v == volume) {
            return;
        }
        volume = v;
        show();
        McuVolume.set(ctx, v);
    }

    private void holdToRepeat(View button, int delta) {
        final Runnable repeater = new Runnable() {
            @Override
            public void run() {
                step(delta);
                handler.postDelayed(this, REPEAT_MS);
            }
        };
        button.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    step(delta);
                    handler.postDelayed(repeater, REPEAT_START_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    v.setPressed(false);
                    handler.removeCallbacks(repeater);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    handler.removeCallbacks(repeater);
                    return true;
                default:
                    return true;
            }
        });
    }
}
