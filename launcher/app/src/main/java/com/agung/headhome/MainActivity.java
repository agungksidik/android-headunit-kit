package com.agung.headhome;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    static final String PREFS = "tiles";
    private static final int REQ_LOCATION = 1;
    private static final long FIX_TIMEOUT_MS = 5000;
    private static final long TURN_ANIM_MS = 600;
    private static final int COMPACT_BELOW_DP = 900;
    private static final int HIDE_DOCK_LABELS_BELOW_DP = 560;
    private static final long WEATHER_TICK_MS = 30 * 60 * 1000;
    private static final long WEATHER_STALE_MS = 60 * 60 * 1000;

    private static final int TILE_COUNT = 6;
    private static final String KEY_TILES_MIGRATED = "migrated_6_tiles";

    private static final ComponentName CARPLAY = new ComponentName("com.zjinnova.zlink",
            "com.zjinnova.android.zlink.features.launcher.CarPlayActivity");
    private static final ComponentName ANDROID_AUTO = new ComponentName("com.zjinnova.zlink",
            "com.zjinnova.android.zlink.features.launcher.AutoActivity");
    private static final ComponentName YOUTUBE = new ComponentName("com.google.android.youtube",
            "com.google.android.youtube.app.honeycomb.Shell$HomeActivity");
    private static final ComponentName CAMERA_360 = new ComponentName("com.ivicar.avm",
            "com.ivicar.modules.main.view.MainActivity");

    /** Defaults of the four-tile version. Slots that were never saved keep these when the app updates. */
    private static final ComponentName[] LEGACY_DEFAULTS = {CARPLAY, ANDROID_AUTO, YOUTUBE, CAMERA_360};

    /** Default components per slot; null where the default is resolved from a package name. */
    private static final ComponentName[] DEFAULT_TILES = {CARPLAY, ANDROID_AUTO, YOUTUBE, null, null, CAMERA_360};
    private static final String[] DEFAULT_PACKAGES = {null, null, null, "org.schabi.newpipe",
            "com.google.android.apps.maps", null};
    private static final int[] TILE_IDS = {R.id.tile_0, R.id.tile_1, R.id.tile_2, R.id.tile_3, R.id.tile_4, R.id.tile_5};

    /** The runtime permission dialog is shown once per process, not on every return to Home. */
    private static boolean permissionsAsked;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TurnDetector turnDetector = new TurnDetector();

    private RoadView road;
    private ImageView car;
    private TextView clockTime;
    private TextView clockDate;
    private TextView speedValue;
    private TextView speedUnit;
    private View btnEdit;
    private ImageView btnEditIcon;
    private TextView btnEditLabel;
    private TextView btnRestartLabel;
    private final View[] tiles = new View[TILE_COUNT];
    private MusicCard music;
    private LocationManager locationManager;
    private SharedPreferences prefs;

    private WeatherRepo weather;
    private Location lastLocation;
    private boolean firstFixSeen;
    private boolean visible;
    private boolean compactLayout;
    private boolean editMode;
    private boolean hasFix;
    private int speedKmh;
    private long lastFixElapsedMs;
    private TurnDetector.Turn shownTurn = TurnDetector.Turn.STRAIGHT;
    private boolean locationActive;

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            onFix(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    };

    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            refreshWeather();
            handler.postDelayed(this, WEATHER_TICK_MS);
        }
    };

    private final Runnable fixWatchdog = new Runnable() {
        @Override
        public void run() {
            if (hasFix && SystemClock.elapsedRealtime() - lastFixElapsedMs > FIX_TIMEOUT_MS) {
                hasFix = false;
                speedKmh = 0;
                turnDetector.reset();
                applyTurn(TurnDetector.Turn.STRAIGHT);
                updateSpeed();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        weather = new WeatherRepo(this);
        migrateTiles();
        buildUi();
    }

    /** Inflates the full or compact layout for the current window width and binds every view. */
    private void buildUi() {
        setContentView(R.layout.activity_main);
        compactLayout = isCompact(getResources().getConfiguration());

        road = findViewById(R.id.road);
        car = findViewById(R.id.car);
        clockTime = findViewById(R.id.clock_time);
        clockDate = findViewById(R.id.clock_date);
        speedValue = findViewById(R.id.speed_value);
        speedUnit = findViewById(R.id.speed_unit);
        btnEdit = findViewById(R.id.btn_edit);
        btnEditIcon = findViewById(R.id.btn_edit_icon);
        btnEditLabel = findViewById(R.id.btn_edit_label);
        btnRestartLabel = findViewById(R.id.btn_restart_label);

        music = new MusicCard(this, findViewById(R.id.music_card));

        btnEdit.setOnClickListener(v -> setEditMode(!editMode));
        findViewById(R.id.btn_restart).setOnClickListener(v -> RestartDialog.show(this));

        for (int i = 0; i < tiles.length; i++) {
            final int slot = i;
            tiles[i] = findViewById(TILE_IDS[i]);
            tiles[i].setOnClickListener(v -> onTileTap(slot));
            tiles[i].setOnLongClickListener(v -> {
                pickTile(slot);
                return true;
            });
        }

        setupDock();
        applyTexts();
        updateClock();
        updateSpeed();
        renderWeather();
        restoreTurn();
        setEditMode(editMode);
        if (visible) {
            music.start();
        }
    }

    private static boolean isCompact(Configuration c) {
        return c.screenWidthDp > 0 && c.screenWidthDp < COMPACT_BELOW_DP;
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, filter);
        updateClock();
        refreshTiles();
        visible = true;
        PlayerService.ensureStarted(this);
        music.start();
        AppList.preload(this);
        handler.removeCallbacks(fixWatchdog);
        handler.postDelayed(fixWatchdog, 1000);
        startLocation();
        firstFixSeen = false;
        renderWeather();
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        visible = false;
        stopLocation();
        handler.removeCallbacks(fixWatchdog);
        handler.removeCallbacks(weatherTick);
        music.stop();
        try {
            unregisterReceiver(timeReceiver);
        } catch (IllegalArgumentException ignored) {
            // Not registered.
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already on Home: leave edit mode.
        if (editMode) {
            setEditMode(false);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyConfiguration(newConfig);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        applyConfiguration(newConfig);
    }

    /** Re-inflates when the window crosses the compact threshold, else just refreshes texts and labels. */
    private void applyConfiguration(Configuration c) {
        if (isCompact(c) != compactLayout) {
            music.stop();
            buildUi();
            return;
        }
        applyTexts();
        updateClock();
        updateSpeed();
        refreshTiles();
        music.refreshTexts();
        renderWeather();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Back on Home does nothing.
    }

    // ---- texts, clock ----

    private void applyTexts() {
        speedUnit.setText(R.string.speed_unit);
        btnEditLabel.setText(editMode ? R.string.done : R.string.edit);
        btnRestartLabel.setText(R.string.restart);
        bindDock();
    }

    private void updateClock() {
        Date now = new Date();
        Locale locale = Locale.getDefault();
        clockTime.setText(new java.text.SimpleDateFormat("HH:mm", locale).format(now));
        String pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        clockDate.setText(new java.text.SimpleDateFormat(pattern, locale).format(now));
        if (weather != null) {
            renderWeather();
        }
    }

    // ---- dock ----

    private static final class DockEntry {
        final int viewId;
        final int icon;
        final int label;

        DockEntry(int viewId, int icon, int label) {
            this.viewId = viewId;
            this.icon = icon;
            this.label = label;
        }
    }

    private static final DockEntry[] DOCK = {
            new DockEntry(R.id.dock_home, R.drawable.ic_home, R.string.dock_home),
            new DockEntry(R.id.dock_music, R.drawable.ic_music, R.string.dock_music),
            new DockEntry(R.id.dock_settings, R.drawable.ic_settings, R.string.dock_settings),
            new DockEntry(R.id.dock_phone, R.drawable.ic_phone, R.string.dock_phone),
            new DockEntry(R.id.dock_dsp, R.drawable.ic_sliders, R.string.dock_dsp),
            new DockEntry(R.id.dock_apps, R.drawable.ic_grid, R.string.dock_apps),
    };

    private void setupDock() {
        findViewById(R.id.dock_music).setOnClickListener(
                v -> startActivity(new Intent(this, PlayerActivity.class)));
        findViewById(R.id.dock_settings).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.dock_phone).setOnClickListener(v -> Launch.start(this, Launch.NWD_PHONE));
        findViewById(R.id.dock_dsp).setOnClickListener(v -> Launch.start(this, Launch.NWD_DSP));
        findViewById(R.id.dock_apps).setOnClickListener(
                v -> startActivity(new Intent(this, AllAppsActivity.class)));
        // Home is the current screen: no-op.
        bindDock();
    }

    private void bindDock() {
        for (int i = 0; i < DOCK.length; i++) {
            DockEntry e = DOCK[i];
            View cell = findViewById(e.viewId);
            int color = getColor(i == 0 ? R.color.accent : R.color.text);
            ImageView icon = cell.findViewById(R.id.dock_icon);
            icon.setImageResource(e.icon);
            icon.setColorFilter(color);
            TextView label = cell.findViewById(R.id.dock_label);
            label.setText(e.label);
            label.setTextColor(color);
            label.setVisibility(getResources().getConfiguration().screenWidthDp < HIDE_DOCK_LABELS_BELOW_DP
                    ? View.GONE : View.VISIBLE);
        }
    }

    // ---- tiles ----

    /** Slots 0 to 3 that were never saved keep the old defaults, so the update does not change them. */
    private void migrateTiles() {
        if (prefs.getBoolean(KEY_TILES_MIGRATED, false)) {
            return;
        }
        SharedPreferences.Editor e = prefs.edit();
        if (isUpdatedInstall()) {
            for (int slot = 0; slot < LEGACY_DEFAULTS.length; slot++) {
                if (!prefs.contains("tile_" + slot)) {
                    e.putString("tile_" + slot, LEGACY_DEFAULTS[slot].flattenToString());
                }
            }
        }
        e.putBoolean(KEY_TILES_MIGRATED, true).apply();
    }

    /** True when this app was installed before and then updated (a fresh install has equal times). */
    private boolean isUpdatedInstall() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.lastUpdateTime > info.firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Restores all six default tiles. Used by Settings, "Reset shortcuts". */
    static void resetTiles(Context ctx) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        for (int slot = 0; slot < TILE_COUNT; slot++) {
            e.remove("tile_" + slot);
        }
        e.putBoolean(KEY_TILES_MIGRATED, true).apply();
    }

    private ComponentName loadTile(int slot) {
        String flat = prefs.getString("tile_" + slot, null);
        if (flat != null) {
            return ComponentName.unflattenFromString(flat);
        }
        if (DEFAULT_TILES[slot] != null) {
            return DEFAULT_TILES[slot];
        }
        // Resolve the launcher activity of the default package (null when it is not installed).
        Intent launch = getPackageManager().getLaunchIntentForPackage(DEFAULT_PACKAGES[slot]);
        return launch != null ? launch.getComponent() : null;
    }

    private void saveTile(int slot, ComponentName cn) {
        prefs.edit().putString("tile_" + slot, cn.flattenToString()).apply();
    }

    private void refreshTiles() {
        PackageManager pm = getPackageManager();
        for (int slot = 0; slot < tiles.length; slot++) {
            View tile = tiles[slot];
            ImageView icon = tile.findViewById(R.id.tile_icon);
            TextView label = tile.findViewById(R.id.tile_label);
            ComponentName cn = loadTile(slot);
            ActivityInfo info = null;
            if (cn != null) {
                try {
                    info = pm.getActivityInfo(cn, 0);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // Not installed or not visible: shown as "Choose app".
                }
            }
            if (info != null) {
                icon.clearColorFilter();
                icon.setImageDrawable(info.loadIcon(pm));
                label.setText(info.loadLabel(pm));
            } else {
                icon.setImageResource(R.drawable.ic_grid);
                icon.setColorFilter(getColor(R.color.muted));
                label.setText(R.string.choose_app);
            }
            tile.setBackgroundResource(editMode ? R.drawable.bg_tile_edit : R.drawable.bg_tile);
            // The compact row is too narrow for the badge. Labels are hidden below 560 dp, like the dock.
            tile.findViewById(R.id.tile_badge).setVisibility(editMode && !compactLayout ? View.VISIBLE : View.GONE);
            label.setVisibility(compactLayout
                    && getResources().getConfiguration().screenWidthDp < HIDE_DOCK_LABELS_BELOW_DP
                    ? View.GONE : View.VISIBLE);
        }
    }

    private void onTileTap(int slot) {
        ComponentName cn = loadTile(slot);
        boolean installed = false;
        if (cn != null) {
            try {
                getPackageManager().getActivityInfo(cn, 0);
                installed = true;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Falls through to the picker.
            }
        }
        if (editMode || !installed) {
            pickTile(slot);
        } else {
            Launch.start(this, cn);
        }
    }

    private void pickTile(int slot) {
        AppPicker.show(this, cn -> {
            saveTile(slot, cn);
            refreshTiles();
        });
    }

    private void setEditMode(boolean on) {
        editMode = on;
        btnEdit.setBackgroundResource(on ? R.drawable.bg_button_active : R.drawable.bg_button);
        int fg = getColor(on ? R.color.dark_icon : R.color.text);
        btnEditLabel.setTextColor(fg);
        btnEditIcon.setColorFilter(fg);
        btnEditLabel.setText(on ? R.string.done : R.string.edit);
        refreshTiles();
    }

    // ---- GPS speed and turn ----

    /** Location (with COARSE, as Android 12+ expects) and notifications, asked once per process. */
    private void requestMissingPermissionsOnce() {
        if (permissionsAsked) {
            return;
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            permissionsAsked = true;
            requestPermissions(missing.toArray(new String[0]), REQ_LOCATION);
        }
    }

    private void startLocation() {
        requestMissingPermissionsOnce();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (locationActive || locationManager == null) {
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener);
            locationActive = true;
        } catch (SecurityException | IllegalArgumentException ignored) {
            // No permission or no GPS provider: speed stays "--".
        }
    }

    private void stopLocation() {
        if (locationActive && locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException ignored) {
                // Permission revoked meanwhile.
            }
        }
        locationActive = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            startLocation();
        }
    }

    private void onFix(Location loc) {
        lastLocation = loc;
        if (!firstFixSeen) {
            firstFixSeen = true;
            refreshWeather();
        }
        hasFix = true;
        lastFixElapsedMs = SystemClock.elapsedRealtime();
        speedKmh = loc.hasSpeed() ? Math.round(loc.getSpeed() * 3.6f) : 0;
        double bearing = loc.hasBearing() ? loc.getBearing() : Double.NaN;
        long timeMs = loc.getElapsedRealtimeNanos() / 1_000_000L;
        applyTurn(turnDetector.update(bearing, speedKmh, timeMs));
        updateSpeed();
    }

    private void updateSpeed() {
        speedValue.setText(hasFix ? String.valueOf(speedKmh) : "--");
        RestartDialog.currentSpeedKmh = hasFix ? speedKmh : 0;
        road.setSpeedKmh(hasFix ? speedKmh : 0);
    }

    /** Puts a freshly inflated car and road into the current turn pose without animating. */
    private void restoreTurn() {
        float rotation = shownTurn == TurnDetector.Turn.LEFT ? -6f : shownTurn == TurnDetector.Turn.RIGHT ? 6f : 0f;
        float dx = shownTurn == TurnDetector.Turn.LEFT ? -14f : shownTurn == TurnDetector.Turn.RIGHT ? 14f : 0f;
        car.setRotation(rotation);
        car.setTranslationX(dx * getResources().getDisplayMetrics().density);
        road.setTurn(shownTurn, false);
    }

    // ---- weather ----

    private void refreshWeather() {
        Location l = lastLocation;
        if (l == null && locationManager != null
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (SecurityException | IllegalArgumentException ignored) {
                // No GPS provider: keep the cached weather.
            }
        }
        if (l != null) {
            weather.refresh(l.getLatitude(), l.getLongitude(), this::renderWeather);
        }
    }

    private void renderWeather() {
        View block = findViewById(R.id.weather_block);
        if (block == null) {
            return;
        }
        WeatherRepo.Snapshot w = weather.cached();
        if (w == null) {
            block.setVisibility(View.GONE);
            return;
        }
        block.setVisibility(View.VISIBLE);
        ((ImageView) findViewById(R.id.weather_icon)).setImageResource(weatherIcon(WeatherCode.kindFor(w.code, w.isDay)));
        ((TextView) findViewById(R.id.weather_temp)).setText(getString(R.string.weather_temp, w.tempC));
        TextView city = findViewById(R.id.weather_city);
        city.setText(w.city);
        city.setVisibility(w.city.isEmpty() ? View.GONE : View.VISIBLE);
        TextView updated = findViewById(R.id.weather_updated);
        if (System.currentTimeMillis() - w.fetchedAtMs > WEATHER_STALE_MS) {
            updated.setText(getString(R.string.weather_updated,
                    new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(w.fetchedAtMs))));
            updated.setVisibility(View.VISIBLE);
        } else {
            updated.setVisibility(View.GONE);
        }
    }

    private static int weatherIcon(WeatherCode.Kind kind) {
        switch (kind) {
            case CLEAR_DAY:
                return R.drawable.ic_weather_clear_day;
            case CLEAR_NIGHT:
                return R.drawable.ic_weather_clear_night;
            case PARTLY_CLOUDY:
                return R.drawable.ic_weather_partly;
            case FOG:
                return R.drawable.ic_weather_fog;
            case RAIN:
                return R.drawable.ic_weather_rain;
            case THUNDERSTORM:
                return R.drawable.ic_weather_thunder;
            case SNOW:
                return R.drawable.ic_weather_snow;
            default:
                return R.drawable.ic_weather_cloudy;
        }
    }

    private void applyTurn(TurnDetector.Turn turn) {
        if (turn == shownTurn) {
            return;
        }
        shownTurn = turn;
        float rotation = turn == TurnDetector.Turn.LEFT ? -6f : turn == TurnDetector.Turn.RIGHT ? 6f : 0f;
        float dx = turn == TurnDetector.Turn.LEFT ? -14f : turn == TurnDetector.Turn.RIGHT ? 14f : 0f;
        float tx = dx * getResources().getDisplayMetrics().density;
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        car.animate().cancel();
        if (animate) {
            car.animate().rotation(rotation).translationX(tx)
                    .setDuration(TURN_ANIM_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        } else {
            car.setRotation(rotation);
            car.setTranslationX(tx);
        }
        road.setTurn(turn, animate);
    }
}
