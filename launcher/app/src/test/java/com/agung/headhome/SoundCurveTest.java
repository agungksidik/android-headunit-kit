package com.agung.headhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class SoundCurveTest {

    @Test
    public void flatPresetIsZeroAboveTheSubBassCut() {
        int[] g = SoundCurve.gains(SoundPreset.builtin("datar"));
        assertEquals(-3, g[0]);
        assertEquals(-2, g[1]);
        assertEquals(-1, g[2]);
        for (int i = 3; i < 32; i++) {
            assertEquals(0, g[i]);
        }
    }

    @Test
    public void flatAtasIsEightWithSubBassCut() {
        int[] g = SoundCurve.gains(SoundPreset.builtin("flat_atas"));
        assertEquals(5, g[0]); // 20 Hz: 8 - 3
        assertEquals(6, g[1]); // 25 Hz: 8 - 2.25 = 5.75
        assertEquals(7, g[2]); // 32 Hz: 8 - 1.2 = 6.8
        for (int i = 3; i < 32; i++) {
            assertEquals(8, g[i]);
        }
    }

    @Test
    public void nendangHasBassAndTrebleBumps() {
        int[] g = SoundCurve.gains(SoundPreset.builtin("nendang"));
        int bass = g[SoundCurve.nearestBand(72)];
        int mid = g[SoundCurve.nearestBand(600)];
        int treble = g[SoundCurve.nearestBand(10000)];
        assertTrue(bass > mid);
        assertTrue(treble > mid);
    }

    @Test
    public void gainsStayWithinTwelveDb() {
        SoundPreset p = new SoundPreset("x", 12, 6, -6, 6, 6);
        p.offs[7] = 12;
        for (int g : SoundCurve.gains(p)) {
            assertTrue(g >= -12 && g <= 12);
        }
        assertEquals(12, SoundCurve.peak(SoundCurve.gains(p)));
    }

    @Test
    public void bandOffsetAddsWholeDb() {
        SoundPreset p = SoundPreset.builtin("datar");
        p.offs[10] = 3;
        assertEquals(3, SoundCurve.gains(p)[10]);
        assertEquals(0, SoundCurve.gains(p)[11]);
    }

    @Test
    public void nearestBandOnLogScale() {
        assertEquals(0, SoundCurve.nearestBand(20));
        assertEquals(31, SoundCurve.nearestBand(20000));
        assertEquals(5, SoundCurve.nearestBand(60));
    }

    @Test
    public void presetCopyIsDeep() {
        SoundPreset a = SoundPreset.builtin("nendang");
        SoundPreset b = a.copy();
        b.offs[0] = 5;
        assertEquals(0, a.offs[0]);
        assertTrue(Arrays.equals(new int[]{5, 3, 1, 1, 2}, new int[]{b.level, b.bass, b.clarity, b.vocal, b.treble}));
    }

    @Test
    public void everyBuiltinPresetStaysAtOrBelowElevenDb() {
        for (String key : SoundPreset.BUILTIN_KEYS) {
            SoundPreset preset = SoundPreset.builtin(key);
            int[] gains = SoundCurve.gains(preset);
            for (int i = 0; i < gains.length; i++) {
                assertTrue(key + " band " + i + " = " + gains[i], gains[i] <= 11);
            }
        }
    }

    @Test
    public void builtinKeysAreStylesThenGenres() {
        assertEquals(SoundPreset.STYLE_KEYS.length + SoundPreset.GENRE_KEYS.length, SoundPreset.BUILTIN_KEYS.length);
        for (String key : SoundPreset.BUILTIN_KEYS) {
            assertTrue(key, SoundPreset.builtin(key) != null);
        }
    }
}
