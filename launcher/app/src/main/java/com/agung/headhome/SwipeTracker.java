package com.agung.headhome;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ProgressBar;
import android.widget.SeekBar;

/**
 * Watches the touches that reach an activity and reports one horizontal swipe. Gestures that start on a slider or
 * on a horizontally scrollable view are ignored. Call {@link #track} first in dispatchTouchEvent.
 */
final class SwipeTracker {

    static final int NONE = 0;
    /** The swipe has just completed: cancel the touch for the children, then act. */
    static final int FIRED = 1;
    /** A swipe already fired: swallow the rest of the gesture. */
    static final int SWALLOW = 2;

    private final float density;
    private final boolean leftSwipe;

    private float downX;
    private float downY;
    private long downTime;
    private boolean tracking;
    private boolean fired;

    /** leftSwipe true detects right-to-left, false detects left-to-right. */
    SwipeTracker(boolean leftSwipe) {
        // Uses system metrics: safe in a field initializer, before the activity has a base context.
        this.density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        this.leftSwipe = leftSwipe;
    }

    int track(MotionEvent ev, View root) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                downX = ev.getRawX();
                downY = ev.getRawY();
                downTime = ev.getEventTime();
                tracking = !startsOnBlockedView(root, downX, downY);
                return NONE;
            case MotionEvent.ACTION_MOVE:
                if (fired) {
                    return SWALLOW;
                }
                if (!tracking) {
                    return NONE;
                }
                float dx = ev.getRawX() - downX;
                float dy = ev.getRawY() - downY;
                long dt = ev.getEventTime() - downTime;
                boolean hit = leftSwipe ? SwipeGesture.isLeftSwipe(dx, dy, dt, density)
                        : SwipeGesture.isRightSwipe(dx, dy, dt, density);
                if (hit) {
                    fired = true;
                    tracking = false;
                    return FIRED;
                }
                return NONE;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean swallow = fired;
                fired = false;
                tracking = false;
                return swallow ? SWALLOW : NONE;
            default:
                return fired ? SWALLOW : NONE;
        }
    }

    /** Sends a cancel to the children so a tile or button under the finger does not click. */
    static void cancelChildren(MotionEvent ev, android.app.Activity activity) {
        MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        activity.getWindow().superDispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private static boolean startsOnBlockedView(View v, float rx, float ry) {
        if (v.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (rx < loc[0] || rx > loc[0] + v.getWidth() || ry < loc[1] || ry > loc[1] + v.getHeight()) {
            return false;
        }
        if (v instanceof SeekBar || v instanceof ProgressBar || v instanceof HorizontalScrollView
                || v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)) {
            return true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (startsOnBlockedView(g.getChildAt(i), rx, ry)) {
                    return true;
                }
            }
        }
        return false;
    }
}
