package com.agung.headhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SoundAdjustTest {

    private static int[] flat(int v) {
        int[] a = new int[32];
        Arrays.fill(a, v);
        return a;
    }

    @Test
    public void limiterCutGrowsOneDbPerTwoSteps() {
        assertEquals(0, SoundAdjust.limiterCut(21));
        assertEquals(1, SoundAdjust.limiterCut(22));
        assertEquals(1, SoundAdjust.limiterCut(23));
        assertEquals(2, SoundAdjust.limiterCut(24));
        assertEquals(3, SoundAdjust.limiterCut(26));
        assertEquals(4, SoundAdjust.limiterCut(28));
        assertEquals(4, SoundAdjust.limiterCut(40));
    }

    @Test
    public void limiterLowersBandsBelow125HzOnly() {
        int[] out = SoundAdjust.apply(flat(5), 28, true, false);
        for (int i = 0; i < 32; i++) {
            int hz = ChipDsp.FREQUENCIES[i];
            assertEquals(hz < 125 ? 1 : 5, out[i]);
        }
    }

    @Test
    public void limiterClampsPeakToTen() {
        int[] out = SoundAdjust.apply(flat(12), 10, true, false);
        for (int v : out) {
            assertEquals(10, v);
        }
    }

    @Test
    public void limiterOffLeavesTheCurve() {
        assertArrayEquals(flat(12), SoundAdjust.apply(flat(12), 30, false, false));
    }

    @Test
    public void smartLoudnessAddsTwoDbAt50To125HzOnLowVolume() {
        int[] out = SoundAdjust.apply(flat(0), 12, false, true);
        for (int i = 0; i < 32; i++) {
            int hz = ChipDsp.FREQUENCIES[i];
            assertEquals(hz >= 50 && hz <= 125 ? 2 : 0, out[i]);
        }
        assertArrayEquals(flat(0), SoundAdjust.apply(flat(0), 13, false, true));
    }

    @Test
    public void inputIsNotModified() {
        int[] in = flat(5);
        SoundAdjust.apply(in, 28, true, true);
        assertArrayEquals(flat(5), in);
    }

    @Test
    public void taperBoostSlidesFromThreeToZero() {
        assertEquals(3, SoundAdjust.taperBoost(0));
        assertEquals(3, SoundAdjust.taperBoost(8));
        assertEquals(2, SoundAdjust.taperBoost(14)); // 1.5 rounds to 2
        assertEquals(0, SoundAdjust.taperBoost(20));
        assertEquals(0, SoundAdjust.taperBoost(35));
    }

    @Test
    public void serbaBisaBoostsOnly40To125HzAtLowVolumeRegardlessOfSwitches() {
        int[] out = SoundAdjust.apply(flat(0), 8, false, false, true);
        for (int i = 0; i < 32; i++) {
            int hz = ChipDsp.FREQUENCIES[i];
            assertEquals(hz >= 40 && hz <= 125 ? 3 : 0, out[i]);
        }
        assertArrayEquals(out, SoundAdjust.apply(flat(0), 8, true, true, true));
    }

    @Test
    public void serbaBisaCutsBassAtHighVolumeAndCapsAtTen() {
        int[] base = SoundCurve.gains(SoundPreset.builtin("serba_bisa"));
        int[] out = SoundAdjust.apply(base, 30, false, false, true);
        for (int i = 0; i < 32; i++) {
            int hz = ChipDsp.FREQUENCIES[i];
            assertEquals(Math.min(10, hz < 125 ? base[i] - 4 : base[i]), out[i]);
            assertTrue(out[i] <= 10);
        }
    }

    @Test
    public void serbaBisaNeverExceedsElevenAtAnyVolume() {
        int[] base = SoundCurve.gains(SoundPreset.builtin("serba_bisa"));
        for (int volume = 0; volume <= 40; volume++) {
            for (int v : SoundAdjust.apply(base, volume, false, false, true)) {
                assertTrue("volume " + volume, v <= 11);
            }
        }
    }
}
