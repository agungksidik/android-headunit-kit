package com.agung.headhome;

/**
 * Detects a left or right turn from a stream of GPS bearings. Pure logic, no Android types.
 * Feed one sample per location fix: bearing in degrees, speed in km/h, time in milliseconds.
 */
public final class TurnDetector {

    public enum Turn { STRAIGHT, LEFT, RIGHT }

    public static final double MIN_SPEED_KMH = 10.0;
    public static final double THRESHOLD_DEG_PER_SEC = 4.0;
    public static final double ALPHA = 0.5;

    private double lastBearing = Double.NaN;
    private long lastTimeMs;
    private double smoothedRate;

    /** Forgets all history, e.g. after losing the GPS fix. */
    public void reset() {
        lastBearing = Double.NaN;
        lastTimeMs = 0;
        smoothedRate = 0;
    }

    public Turn update(double bearingDeg, double speedKmh, long timeMillis) {
        if (speedKmh < MIN_SPEED_KMH || Double.isNaN(bearingDeg)) {
            reset();
            return Turn.STRAIGHT;
        }
        if (Double.isNaN(lastBearing)) {
            lastBearing = bearingDeg;
            lastTimeMs = timeMillis;
            smoothedRate = 0;
            return Turn.STRAIGHT;
        }
        long dtMs = timeMillis - lastTimeMs;
        if (dtMs <= 0) {
            return classify(smoothedRate);
        }
        double rate = signedDelta(lastBearing, bearingDeg) / (dtMs / 1000.0);
        smoothedRate = ALPHA * rate + (1 - ALPHA) * smoothedRate;
        lastBearing = bearingDeg;
        lastTimeMs = timeMillis;
        return classify(smoothedRate);
    }

    /** Signed change from one bearing to another in (-180, 180], handling the 359 to 1 wrap. */
    static double signedDelta(double from, double to) {
        double d = (to - from) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    private static Turn classify(double rate) {
        if (rate > THRESHOLD_DEG_PER_SEC) {
            return Turn.RIGHT;
        }
        if (rate < -THRESHOLD_DEG_PER_SEC) {
            return Turn.LEFT;
        }
        return Turn.STRAIGHT;
    }
}
