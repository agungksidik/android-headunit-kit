package com.agung.headhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherCodeTest {

    @Test
    public void clearDependsOnDaylight() {
        assertEquals(WeatherCode.Kind.CLEAR_DAY, WeatherCode.kindFor(0, true));
        assertEquals(WeatherCode.Kind.CLEAR_NIGHT, WeatherCode.kindFor(0, false));
    }

    @Test
    public void cloudAndFog() {
        assertEquals(WeatherCode.Kind.PARTLY_CLOUDY, WeatherCode.kindFor(1, true));
        assertEquals(WeatherCode.Kind.PARTLY_CLOUDY, WeatherCode.kindFor(2, false));
        assertEquals(WeatherCode.Kind.CLOUDY, WeatherCode.kindFor(3, true));
        assertEquals(WeatherCode.Kind.FOG, WeatherCode.kindFor(45, true));
        assertEquals(WeatherCode.Kind.FOG, WeatherCode.kindFor(48, true));
    }

    @Test
    public void rainGroups() {
        for (int c : new int[]{51, 55, 61, 65, 66, 67, 80, 81, 82}) {
            assertEquals("code " + c, WeatherCode.Kind.RAIN, WeatherCode.kindFor(c, true));
        }
    }

    @Test
    public void thunderstormAndSnow() {
        for (int c : new int[]{95, 96, 99}) {
            assertEquals(WeatherCode.Kind.THUNDERSTORM, WeatherCode.kindFor(c, true));
        }
        for (int c : new int[]{71, 73, 75, 77, 85, 86}) {
            assertEquals("code " + c, WeatherCode.Kind.SNOW, WeatherCode.kindFor(c, false));
        }
    }

    @Test
    public void unknownCodeFallsBackToCloudy() {
        assertEquals(WeatherCode.Kind.CLOUDY, WeatherCode.kindFor(1234, true));
    }
}
