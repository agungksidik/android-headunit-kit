package com.agung.headhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Current weather from Open-Meteo (no key), cached in SharedPreferences. */
final class WeatherRepo {

    static final class Snapshot {
        final int tempC;
        final int code;
        final boolean isDay;
        final String city;
        final long fetchedAtMs;

        Snapshot(int tempC, int code, boolean isDay, String city, long fetchedAtMs) {
            this.tempC = tempC;
            this.code = code;
            this.isDay = isDay;
            this.city = city;
            this.fetchedAtMs = fetchedAtMs;
        }
    }

    private static final String PREFS = "weather";
    private static final long MIN_ATTEMPT_INTERVAL_MS = 10 * 60 * 1000;
    private static final int TIMEOUT_MS = 10_000;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private long lastAttemptElapsed = -1;

    WeatherRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The cached weather, or null when nothing was ever fetched. */
    Snapshot cached() {
        if (!prefs.contains("fetched")) {
            return null;
        }
        return new Snapshot(prefs.getInt("temp", 0), prefs.getInt("code", 3), prefs.getBoolean("day", true),
                shortPlaceName(prefs.getString("city", "")), prefs.getLong("fetched", 0));
    }

    /**
     * Fetches the weather for these coordinates (rounded to 2 decimals) at most once per 10 minutes.
     * onDone runs on the main thread after a successful fetch.
     */
    void refresh(double lat, double lon, Runnable onDone) {
        long now = SystemClock.elapsedRealtime();
        if (running || (lastAttemptElapsed >= 0 && now - lastAttemptElapsed < MIN_ATTEMPT_INTERVAL_MS)) {
            return;
        }
        lastAttemptElapsed = now;
        running = true;
        final double rlat = round2(lat);
        final double rlon = round2(lon);
        new Thread(() -> {
            try {
                if (fetch(rlat, rlon)) {
                    main.post(onDone);
                }
            } finally {
                running = false;
            }
        }, "weather").start();
    }

    private static final String[] ADMIN_PREFIXES = {
            "Kota Administrasi", "Kecamatan", "Kelurahan", "Kabupaten", "Kota", "Kec.", "Kel.", "Kab.", "Desa"
    };

    /** Drops a leading administrative word ("Kecamatan Sukamaju" becomes "Sukamaju"). */
    static String shortPlaceName(String name) {
        if (name == null) {
            return "";
        }
        String n = name.trim();
        for (String prefix : ADMIN_PREFIXES) {
            if (n.length() > prefix.length() && n.regionMatches(true, 0, prefix, 0, prefix.length())
                    && n.charAt(prefix.length()) == ' ') {
                return n.substring(prefix.length() + 1).trim();
            }
        }
        return n;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private boolean fetch(double lat, double lon) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                            + "&current=temperature_2m,weather_code,is_day&timezone=auto", lat, lon));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            JSONObject current = new JSONObject(readAll(conn.getInputStream())).getJSONObject("current");
            int temp = (int) Math.round(current.getDouble("temperature_2m"));
            int code = current.getInt("weather_code");
            boolean day = current.optInt("is_day", 1) == 1;

            String key = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon);
            String city = prefs.getString("city", "");
            if (!key.equals(prefs.getString("city_key", null)) || city.isEmpty()) {
                String found = geocode(lat, lon);
                if (found != null) {
                    city = found;
                    prefs.edit().putString("city_key", key).apply();
                } else if (!key.equals(prefs.getString("city_key", null))) {
                    city = "";
                }
            }
            prefs.edit()
                    .putInt("temp", temp)
                    .putInt("code", code)
                    .putBoolean("day", day)
                    .putString("city", city)
                    .putLong("fetched", System.currentTimeMillis())
                    .apply();
            return true;
        } catch (IOException | org.json.JSONException | RuntimeException e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** City name from the platform Geocoder: locality, else subAdminArea. Null when unavailable. */
    @SuppressWarnings("deprecation")
    private String geocode(double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) {
                return null;
            }
            List<Address> list = new Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) {
                return null;
            }
            Address a = list.get(0);
            String name = a.getLocality();
            if (name == null || name.isEmpty()) {
                name = a.getSubAdminArea();
            }
            return name == null || name.isEmpty() ? null : shortPlaceName(name);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
