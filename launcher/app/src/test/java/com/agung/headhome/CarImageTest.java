package com.agung.headhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CarImageTest {

    @Test
    public void sampleSizeKeepsLongestSideUnder800() {
        assertEquals(1, CarImage.sampleSize(800, 600));
        assertEquals(2, CarImage.sampleSize(1600, 1200));
        assertEquals(4, CarImage.sampleSize(3000, 2000));
        assertEquals(2, CarImage.sampleSize(10, 801));
        assertEquals(1, CarImage.sampleSize(352, 382));
    }
}
