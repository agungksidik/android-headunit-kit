package com.agung.headhome;

/** The curve math of the Custom Sound design, ported from its script. Pure logic. */
public final class SoundCurve {

    public static final int MIN_DB = -12;
    public static final int MAX_DB = 12;

    private SoundCurve() {
    }

    private static double bump(double lg, double center, double width) {
        double x = (lg - Math.log10(center)) / width;
        return Math.exp(-x * x);
    }

    /** The unrounded gain of one band for the five controls plus its offset. */
    public static double raw(SoundPreset p, int freq, int index) {
        double lg = Math.log10(freq);
        double g = p.level
                + p.bass * bump(lg, 72, 0.22) * 1.15
                - p.clarity * bump(lg, 240, 0.14) * 0.9
                + p.vocal * bump(lg, 1800, 0.25)
                + p.treble * bump(lg, 10000, 0.2);
        if (freq < 40) {
            g -= (40 - freq) / 20.0 * 3;
        }
        return g + p.offs[index];
    }

    /** Gains in whole dB (the factory scale has 1 dB steps), limited to -12 to +12. */
    public static int[] gains(SoundPreset p) {
        int[] out = new int[ChipDsp.FREQUENCIES.length];
        for (int i = 0; i < out.length; i++) {
            long g = Math.round(raw(p, ChipDsp.FREQUENCIES[i], i));
            out[i] = (int) Math.max(MIN_DB, Math.min(MAX_DB, g));
        }
        return out;
    }

    public static int peak(int[] gains) {
        int max = Integer.MIN_VALUE;
        for (int g : gains) {
            max = Math.max(max, g);
        }
        return max;
    }

    /** Index of the band closest to a frequency on a log scale. */
    public static int nearestBand(int hz) {
        int best = 0;
        for (int i = 0; i < ChipDsp.FREQUENCIES.length; i++) {
            double a = Math.abs(Math.log10((double) ChipDsp.FREQUENCIES[i] / hz));
            double b = Math.abs(Math.log10((double) ChipDsp.FREQUENCIES[best] / hz));
            if (a < b) {
                best = i;
            }
        }
        return best;
    }
}
