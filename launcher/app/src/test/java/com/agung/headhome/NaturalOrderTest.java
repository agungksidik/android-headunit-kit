package com.agung.headhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NaturalOrderTest {

    private static List<String> sorted(String... in) {
        List<String> l = new ArrayList<>(Arrays.asList(in));
        Collections.sort(l, NaturalOrder.INSTANCE);
        return l;
    }

    @Test
    public void digitRunsCompareByValue() {
        assertEquals(Arrays.asList("01", "2", "10"), sorted("10", "2", "01"));
        assertEquals(Arrays.asList("track 2.mp3", "track 10.mp3", "track 11.mp3"),
                sorted("track 11.mp3", "track 2.mp3", "track 10.mp3"));
    }

    @Test
    public void caseInsensitive() {
        assertEquals(Arrays.asList("abba", "Beatles", "coldplay"), sorted("coldplay", "Beatles", "abba"));
        assertTrue(NaturalOrder.INSTANCE.compare("a.Mp3", "B.mp3") < 0);
    }

    @Test
    public void foldersByPath() {
        assertEquals(Arrays.asList("/mnt/MP3/1 Album", "/mnt/MP3/2 Album", "/mnt/MP3/10 Album"),
                sorted("/mnt/MP3/10 Album", "/mnt/MP3/1 Album", "/mnt/MP3/2 Album"));
    }

    @Test
    public void equalNumbersStillOrderDeterministically() {
        assertTrue(NaturalOrder.INSTANCE.compare("01", "1") != 0);
        assertEquals(0, NaturalOrder.INSTANCE.compare("x", "x"));
    }
}
