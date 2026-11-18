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

    public static int[] apply(int[] userDb, int volume, boolean limiter, boolean smartLoudness) {
        int[] out = userDb.clone();
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
