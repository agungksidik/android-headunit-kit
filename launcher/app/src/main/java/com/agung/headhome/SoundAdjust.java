package com.agung.headhome;

/**
 * Adjustments applied on top of the user curve when it is sent, never saved into presets.
 * Anti-clip: from volume 22 the bands below 125 Hz go down 1 dB per 2 volume steps (at most 4 dB), and every
 * band stays at +10 dB or lower. Smart loudness: at volume 12 or lower the 50 to 125 Hz bands get +2 dB.
 */
public final class SoundAdjust {

    public static final int LIMITER_FROM_VOLUME = 22;
    public static final int LIMITER_MAX_CUT_DB = 4;
    public static final int PEAK_LIMIT_DB = 10;
    public static final int LOUDNESS_UP_TO_VOLUME = 12;
    public static final int LOUDNESS_BOOST_DB = 2;

    private SoundAdjust() {
    }

    /** The cut in dB for the anti-clip function at this volume. */
    public static int limiterCut(int volume) {
        if (volume < LIMITER_FROM_VOLUME) {
            return 0;
        }
        return Math.min(LIMITER_MAX_CUT_DB, (volume - (LIMITER_FROM_VOLUME - 2)) / 2);
    }

    public static final int TAPER_BOOST_DB = 3;
    public static final int TAPER_FULL_UP_TO_VOLUME = 8;
    public static final int TAPER_ZERO_FROM_VOLUME = 20;

    /** The extra dB for the 40 to 125 Hz bands of Serba Bisa at this volume (3 up to volume 8, 0 from volume 20). */
    public static int taperBoost(int volume) {
        if (volume <= TAPER_FULL_UP_TO_VOLUME) {
            return TAPER_BOOST_DB;
        }
        if (volume >= TAPER_ZERO_FROM_VOLUME) {
            return 0;
        }
        double t = (double) (TAPER_ZERO_FROM_VOLUME - volume) / (TAPER_ZERO_FROM_VOLUME - TAPER_FULL_UP_TO_VOLUME);
        return (int) Math.round(TAPER_BOOST_DB * t);
    }

    public static int[] apply(int[] userDb, int volume, boolean limiter, boolean smartLoudness) {
        return apply(userDb, volume, limiter, smartLoudness, false);
    }

    /** serbaBisa true: use the preset's own volume taper and always apply anti-clip, whatever the switches say. */
    public static int[] apply(int[] userDb, int volume, boolean limiter, boolean smartLoudness, boolean serbaBisa) {
        int[] out = userDb.clone();
        if (serbaBisa) {
            int boost = taperBoost(volume);
            for (int i = 0; i < out.length; i++) {
                int f = ChipDsp.FREQUENCIES[i];
                if (f >= 40 && f <= 125) {
                    out[i] += boost;
                }
            }
            limiter = true;
            smartLoudness = false;
        }
        if (smartLoudness && volume <= LOUDNESS_UP_TO_VOLUME) {
            for (int i = 0; i < out.length; i++) {
                int f = ChipDsp.FREQUENCIES[i];
                if (f >= 50 && f <= 125) {
                    out[i] += LOUDNESS_BOOST_DB;
                }
            }
        }
        if (limiter) {
            int cut = limiterCut(volume);
            for (int i = 0; i < out.length; i++) {
                if (ChipDsp.FREQUENCIES[i] < 125) {
                    out[i] -= cut;
                }
                out[i] = Math.min(out[i], PEAK_LIMIT_DB);
            }
        }
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.max(SoundCurve.MIN_DB, Math.min(SoundCurve.MAX_DB, out[i]));
        }
        return out;
    }
}
