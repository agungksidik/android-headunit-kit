package com.agung.headhome;

/** Maps WMO weather codes (as returned by Open-Meteo) to the icon kinds the clock card can show. Pure logic. */
public final class WeatherCode {

    public enum Kind { CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, THUNDERSTORM, SNOW }

    private WeatherCode() {
    }

    public static Kind kindFor(int code, boolean isDay) {
        if (code == 0) {
            return isDay ? Kind.CLEAR_DAY : Kind.CLEAR_NIGHT;
        }
        if (code == 1 || code == 2) {
            return Kind.PARTLY_CLOUDY;
        }
        if (code == 3) {
            return Kind.CLOUDY;
        }
        if (code == 45 || code == 48) {
            return Kind.FOG;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return Kind.RAIN;
        }
        if (code >= 95 && code <= 99) {
            return Kind.THUNDERSTORM;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return Kind.SNOW;
        }
        return Kind.CLOUDY;
    }
}
