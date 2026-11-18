package com.agung.headhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * The volume popup. The factory popup belongs to the factory launcher process, which stops when another Home is
 * set, so this app draws its own whenever the MCU volume changes. It is an overlay window when the app may draw
 * over other apps (appops SYSTEM_ALERT_WINDOW), else a view on top of this app's own screens. Never focusable
 * or touchable. Started from the Application class and from the player service so it survives either.
 */
final class VolumePopup {

    private static final long FADE_MS = 150;
    private static final long HIDE_AFTER_MS = 1800;

    private static VolumePopup instance;

    static synchronized VolumePopup get(Context ctx) {
        if (instance == null) {
            instance = new VolumePopup((Application) ctx.getApplicationContext());
        }
        return instance;
    }

    private enum Mount { NONE, OVERLAY, ACTIVITY }

    private final Application app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;

    private boolean started;
    private int lastVolume = -1;
    private View card;
    private ImageView icon;
    private ProgressBar bar;
    private TextView value;
    private Mount mount = Mount.NONE;
    private Activity top;
    private ViewGroup activityParent;
    private ContentObserver observer;

    private final Runnable hideRunnable = this::hide;

    private VolumePopup(Application app) {
        this.app = app;
        this.density = app.getResources().getDisplayMetrics().density;
    }

    /** Starts watching the volume. The current value is read and ignored, so nothing shows at start. */
    synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        lastVolume = McuVolume.get(app);
        observer = McuVolume.observe(app, handler, this::onVolumeChanged);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                top = activity;
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (top == activity) {
                    if (mount == Mount.ACTIVITY) {
                        detach();
                    }
                    top = null;
                }
            }

            @Override
            public void onActivityCreated(Activity a, Bundle b) {
            }

            @Override
            public void onActivityStarted(Activity a) {
            }

            @Override
            public void onActivityStopped(Activity a) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity a, Bundle b) {
            }

            @Override
            public void onActivityDestroyed(Activity a) {
            }
        });
    }

    private void onVolumeChanged() {
        int v = McuVolume.get(app);
        if (v == lastVolume) {
            return;
        }
        lastVolume = v;
        show(v, McuVolume.max(app));
    }

    private void ensureCard() {
        if (card != null) {
            return;
        }
        Context themed = new ContextThemeWrapper(app, R.style.Theme_HeadHome);
        card = LayoutInflater.from(themed).inflate(R.layout.volume_popup, null);
        icon = card.findViewById(R.id.volume_popup_icon);
        bar = card.findViewById(R.id.volume_popup_bar);
        value = card.findViewById(R.id.volume_popup_value);
    }

    private void show(int volume, int max) {
        ensureCard();
        bar.setMax(max);
        bar.setProgress(volume);
        value.setText(String.valueOf(volume));
        icon.setImageResource(volume <= 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume);
        icon.setColorFilter(app.getColor(volume <= 0 ? R.color.muted : R.color.accent));

        boolean wasShown = mount != Mount.NONE;
        if (!wasShown) {
            attach();
            if (mount == Mount.NONE) {
                return;
            }
            fade(0f, 1f);
        }
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, HIDE_AFTER_MS);
    }

    private void attach() {
        if (Settings.canDrawOverlays(app)) {
            try {
                WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        (int) (592 * density), (int) (128 * density),
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.y = (int) (44 * density);
                card.setAlpha(0f);
                wm.addView(card, lp);
                mount = Mount.OVERLAY;
                return;
            } catch (RuntimeException e) {
                // Fall back to the in-app popup below.
            }
        }
        if (top != null) {
            ViewGroup content = top.findViewById(android.R.id.content);
            if (content != null) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (592 * density),
                        (int) (128 * density), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                lp.topMargin = 0;
                card.setAlpha(0f);
                content.addView(card, lp);
                activityParent = content;
                mount = Mount.ACTIVITY;
            }
        }
    }

    private void fade(float from, float to) {
        if (card == null) {
            return;
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            card.setAlpha(to);
            return;
        }
        card.setAlpha(from);
        card.animate().alpha(to).setDuration(FADE_MS).start();
    }

    private void hide() {
        if (mount == Mount.NONE || card == null) {
            return;
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            detach();
            return;
        }
        card.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (!handler.hasCallbacks(hideRunnable)) {
                detach();
            }
        }).start();
    }

    private void detach() {
        handler.removeCallbacks(hideRunnable);
        if (card == null) {
            return;
        }
        card.animate().cancel();
        try {
            if (mount == Mount.OVERLAY) {
                ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(card);
            } else if (mount == Mount.ACTIVITY && activityParent != null) {
                activityParent.removeView(card);
            }
        } catch (RuntimeException ignored) {
            // Already removed with its window.
        }
        activityParent = null;
        mount = Mount.NONE;
    }
}
