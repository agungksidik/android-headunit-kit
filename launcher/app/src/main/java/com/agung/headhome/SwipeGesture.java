package com.agung.headhome;

/** The math of a horizontal swipe: far enough, mostly horizontal, and quick. Pure logic. */
public final class SwipeGesture {

    public static final float MIN_DISTANCE_DP = 120f;
    public static final float HORIZONTAL_RATIO = 2f;
    public static final long MAX_DURATION_MS = 600;

    private SwipeGesture() {
    }

    /** Finger moved right to left. dx and dy are in pixels. */
    public static boolean isLeftSwipe(float dx, float dy, long durationMs, float density) {
        return matches(-dx, dy, durationMs, density);
    }

    /** Finger moved left to right. */
    public static boolean isRightSwipe(float dx, float dy, long durationMs, float density) {
        return matches(dx, dy, durationMs, density);
    }

    private static boolean matches(float forward, float dy, long durationMs, float density) {
        return forward >= MIN_DISTANCE_DP * density
                && forward > HORIZONTAL_RATIO * Math.abs(dy)
                && durationMs >= 0
                && durationMs <= MAX_DURATION_MS;
    }
}
