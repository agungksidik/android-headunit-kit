package com.agung.headhome;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * State and chip output of Custom Sound: the working curve, built-in and user presets, anti-clip and smart
 * loudness, A/B and the listening position. Every change sends the full 32-band packet to the DSP chip
 * (debounced 120 ms). Lives for the whole process; all state methods are for the main thread.
 */
final class SoundEngine {

    interface Listener {
        void onSoundChanged();
    }

    static final int MAX_USER_PRESETS = 10;
    static final String ID_NONE = "none";
    static final String ID_CUSTOM = "custom";
    static final String ID_BUILTIN = "builtin:";
    static final String ID_USER = "user:";
    static final String SERBA_BISA_ID = ID_BUILTIN + "serba_bisa";

    /** Fader and balance per listening position: driver (right-hand drive), front, all seats, rear. */
    static final int[][] SEAT_SITE = {{3, 11}, {3, 7}, {7, 7}, {12, 7}};

    private static final long SEND_DEBOUNCE_MS = 120;
    private static final long SAVE_DEBOUNCE_MS = 500;
    private static final long RESTORE_DELAY_MS = 2500;
    private static final long TOAST_GAP_MS = 5000;
    private static final String STATE_FILE = "sound_state.json";

    private static SoundEngine instance;

    static synchronized SoundEngine get(Context ctx) {
        if (instance == null) {
            instance = new SoundEngine(ctx.getApplicationContext());
        }
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final File stateFile;

    final SoundPreset working = SoundPreset.builtin("datar");
    final List<SoundPreset> users = new ArrayList<>();
    /** Nothing is selected and nothing has been sent until the user picks a preset or changes a control. */
    String activeId = ID_NONE;
    boolean applied;
    boolean limiter = true;
    boolean smartLoudness = true;
    boolean showB;
    int seat;

    private boolean hasSavedState;
    private boolean restoreDone;
    private boolean everSent;
    private boolean loudnessSent;
    private boolean siteDirty;
    private int[] lastSentEffective;
    private int gainMax = -1;
    private long lastToast;
    private ContentObserver volumeObserver;

    private final Runnable sendRunnable = this::sendNow;
    private final Runnable saveRunnable = this::saveNow;

    private SoundEngine(Context app) {
        this.app = app;
        this.stateFile = new File(app.getFilesDir(), STATE_FILE);
        load();
        volumeObserver = McuVolume.observe(app, main, this::onVolumeChanged);
    }

    // ---- listeners ----

    void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        applied = true;
        for (Listener l : listeners) {
            l.onSoundChanged();
        }
        scheduleSend();
        scheduleSave();
    }

    // ---- derived values ----

    int[] userGains() {
        return SoundCurve.gains(working);
    }

    boolean isSerbaBisa() {
        return SERBA_BISA_ID.equals(activeId);
    }

    /** What is sent to the chip: flat for B, else the curve with anti-clip and smart loudness applied. */
    int[] effectiveGains() {
        if (showB) {
            return new int[SoundPreset.BANDS];
        }
        return SoundAdjust.apply(userGains(), McuVolume.get(app), limiter, smartLoudness, isSerbaBisa());
    }

    /** The name shown for the active setting. */
    String activeLabel(Context ctx) {
        if (!applied) {
            return "";
        }
        if (activeId.startsWith(ID_BUILTIN)) {
            return builtinLabel(ctx, activeId.substring(ID_BUILTIN.length()));
        }
        if (activeId.startsWith(ID_USER)) {
            return activeId.substring(ID_USER.length());
        }
        return ctx.getString(R.string.sound_preset_mine);
    }

    static String builtinLabel(Context ctx, String key) {
        switch (key) {
            case "flat_atas":
                return ctx.getString(R.string.sound_preset_flat_atas);
            case "nendang":
                return ctx.getString(R.string.sound_preset_nendang);
            case "vokal":
                return ctx.getString(R.string.sound_preset_vokal);
            case "datar":
                return ctx.getString(R.string.sound_preset_datar);
            case "koplo":
                return ctx.getString(R.string.sound_preset_koplo);
            case "dangdut":
                return ctx.getString(R.string.sound_preset_dangdut);
            case "akustik":
                return ctx.getString(R.string.sound_preset_akustik);
            case "rock":
                return ctx.getString(R.string.sound_preset_rock);
            case "jazz":
                return ctx.getString(R.string.sound_preset_jazz);
            case "dj_edm":
                return ctx.getString(R.string.sound_preset_dj_edm);
            case "pop":
                return ctx.getString(R.string.sound_preset_pop);
            case "serba_bisa":
                return ctx.getString(R.string.sound_preset_serba_bisa);
            default:
                return ctx.getString(R.string.sound_preset_malam);
        }
    }

    // ---- user actions ----

    void pickBuiltin(String key) {
        working.copyFrom(SoundPreset.builtin(key));
        activeId = ID_BUILTIN + key;
        showB = false;
        changed();
    }

    void pickUser(String name) {
        for (SoundPreset p : users) {
            if (p.name.equals(name)) {
                working.copyFrom(p);
                activeId = ID_USER + name;
                showB = false;
                changed();
                return;
            }
        }
    }

    /** The "Preset Saya" card: back to the unsaved working curve. */
    void pickCustom() {
        activeId = ID_CUSTOM;
        showB = false;
        changed();
    }

    void step(String key, int delta) {
        int lo = "level".equals(key) ? -12 : -6;
        int hi = -lo;
        switch (key) {
            case "level":
                working.level = Math.max(lo, Math.min(hi, working.level + delta));
                break;
            case "bass":
                working.bass = Math.max(lo, Math.min(hi, working.bass + delta));
                break;
            case "clarity":
                working.clarity = Math.max(lo, Math.min(hi, working.clarity + delta));
                break;
            case "vocal":
                working.vocal = Math.max(lo, Math.min(hi, working.vocal + delta));
                break;
            default:
                working.treble = Math.max(lo, Math.min(hi, working.treble + delta));
                break;
        }
        edited();
    }

    void stepBand(int band, int delta) {
        working.offs[band] = Math.max(-12, Math.min(12, working.offs[band] + delta));
        edited();
    }

    void flatten() {
        working.bass = 0;
        working.clarity = 0;
        working.vocal = 0;
        working.treble = 0;
        Arrays.fill(working.offs, 0);
        edited();
    }

    private void edited() {
        activeId = ID_CUSTOM;
        showB = false;
        changed();
    }

    void setLimiter(boolean on) {
        limiter = on;
        changed();
    }

    void setSmartLoudness(boolean on) {
        smartLoudness = on;
        changed();
    }

    void setSeat(int index) {
        seat = Math.max(0, Math.min(SEAT_SITE.length - 1, index));
        siteDirty = true;
        changed();
    }

    void toggleB() {
        showB = !showB;
        changed();
    }

    /** Saves the working curve as a user preset. Returns false when the limit of 10 is reached. */
    boolean saveAs(String name) {
        String n = name.trim();
        if (n.isEmpty()) {
            return false;
        }
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).name.equalsIgnoreCase(n)) {
                SoundPreset p = working.copy();
                p.name = users.get(i).name;
                users.set(i, p);
                activeId = ID_USER + p.name;
                changed();
                return true;
            }
        }
        if (users.size() >= MAX_USER_PRESETS) {
            return false;
        }
        SoundPreset p = working.copy();
        p.name = n;
        users.add(p);
        activeId = ID_USER + n;
        changed();
        return true;
    }

    void rename(String oldName, String newName) {
        String n = newName.trim();
        if (n.isEmpty()) {
            return;
        }
        for (SoundPreset p : users) {
            if (p.name.equals(oldName)) {
                p.name = n;
                if (activeId.equals(ID_USER + oldName)) {
                    activeId = ID_USER + n;
                }
                changed();
                return;
            }
        }
    }

    void delete(String name) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).name.equals(name)) {
                users.remove(i);
                if (activeId.equals(ID_USER + name)) {
                    activeId = ID_CUSTOM;
                }
                changed();
                return;
            }
        }
    }

    // ---- boot and volume ----

    /** Sends the saved setting once per process, a little after start, so the chip gets it after every boot. */
    void restoreOnce() {
        if (restoreDone) {
            return;
        }
        restoreDone = true;
        if (applied && hasSavedState) {
            loudnessSent = false;
            siteDirty = true;
            main.postDelayed(this::sendNow, RESTORE_DELAY_MS);
        }
    }

    private void onVolumeChanged() {
        if (!applied) {
            return;
        }
        if (!Arrays.equals(effectiveGains(), lastSentEffective)) {
            scheduleSend();
        }
        for (Listener l : listeners) {
            l.onSoundChanged();
        }
    }

    // ---- sending ----

    private void scheduleSend() {
        main.removeCallbacks(sendRunnable);
        main.postDelayed(sendRunnable, SEND_DEBOUNCE_MS);
    }

    private void sendNow() {
        final int[] effective = effectiveGains();
        final int[] site = SEAT_SITE[seat];
        final boolean sendLoudness = !loudnessSent;
        final boolean sendSite = siteDirty || !everSent;
        everSent = true;
        loudnessSent = true;
        siteDirty = false;
        lastSentEffective = effective;
        io.execute(() -> {
            if (gainMax < 0) {
                gainMax = ChipDsp.readIntProperty(new File(ChipDsp.UART_CONFIG_PATH), ChipDsp.GAIN_MAX_KEY,
                        ChipDsp.DEFAULT_GAIN_MAX);
            }
            int[] chip = ChipDsp.dbToChip(effective, gainMax);
            int[] q = new int[SoundPreset.BANDS];
            Arrays.fill(q, ChipDsp.Q_VALUE);
            ChipDsp.Result result = ChipDsp.sendPacket(ChipDsp.eqPacket(ChipDsp.FREQUENCIES, q, chip));
            if (result == ChipDsp.Result.OK && sendLoudness) {
                // Custom Sound has its own smart loudness, so the factory loudness stays off.
                result = ChipDsp.sendPacket(ChipDsp.loudnessPacket(false));
            }
            if (result == ChipDsp.Result.OK && sendSite) {
                result = ChipDsp.sendPacket(ChipDsp.sitePacket(site[0], site[1]));
            }
            if (result != ChipDsp.Result.OK) {
                final ChipDsp.Result failure = result;
                main.post(() -> onSendFailed(failure));
            }
        });
    }

    private boolean blockedPending;

    private void onSendFailed(ChipDsp.Result result) {
        if (result == ChipDsp.Result.BLOCKED) {
            blockedPending = true;
            // An open Custom Sound screen shows the explanation dialog once. Otherwise a toast is used.
            for (Listener l : listeners) {
                l.onSoundChanged();
            }
            if (!blockedPending) {
                return;
            }
        }
        toastFailure();
    }

    /** True once, the first time the hidden API block is seen: the screen then explains the adb command. */
    boolean consumeBlockedNotice() {
        if (!blockedPending) {
            return false;
        }
        android.content.SharedPreferences prefs = app.getSharedPreferences("sound", Context.MODE_PRIVATE);
        if (prefs.getBoolean("blocked_notice_shown", false)) {
            return false;
        }
        prefs.edit().putBoolean("blocked_notice_shown", true).apply();
        blockedPending = false;
        return true;
    }

    private void toastFailure() {
        long now = SystemClock.elapsedRealtime();
        if (lastToast == 0 || now - lastToast > TOAST_GAP_MS) {
            lastToast = now;
            Toast.makeText(app, R.string.sound_no_response, Toast.LENGTH_LONG).show();
        }
    }

    // ---- persistence ----

    private void scheduleSave() {
        main.removeCallbacks(saveRunnable);
        main.postDelayed(saveRunnable, SAVE_DEBOUNCE_MS);
    }

    private static JSONObject toJson(SoundPreset p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", p.name);
        o.put("level", p.level);
        o.put("bass", p.bass);
        o.put("clarity", p.clarity);
        o.put("vocal", p.vocal);
        o.put("treble", p.treble);
        JSONArray offs = new JSONArray();
        for (int v : p.offs) {
            offs.put(v);
        }
        o.put("offs", offs);
        return o;
    }

    private static SoundPreset fromJson(JSONObject o) {
        SoundPreset p = new SoundPreset(o.optString("name", ""), o.optInt("level"), o.optInt("bass"),
                o.optInt("clarity"), o.optInt("vocal"), o.optInt("treble"));
        JSONArray offs = o.optJSONArray("offs");
        if (offs != null) {
            for (int i = 0; i < SoundPreset.BANDS && i < offs.length(); i++) {
                p.offs[i] = offs.optInt(i);
            }
        }
        return p;
    }

    private void saveNow() {
        final String json;
        try {
            JSONObject o = new JSONObject();
            o.put("active", activeId);
            o.put("applied", applied);
            o.put("working", toJson(working));
            o.put("limiter", limiter);
            o.put("smartLoudness", smartLoudness);
            o.put("seat", seat);
            JSONArray arr = new JSONArray();
            for (SoundPreset p : users) {
                arr.put(toJson(p));
            }
            o.put("users", arr);
            json = o.toString();
        } catch (JSONException e) {
            return;
        }
        io.execute(() -> {
            File tmp = new File(stateFile.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                return;
            }
            if (!tmp.renameTo(stateFile)) {
                tmp.delete();
            }
        });
        hasSavedState = true;
    }

    private void load() {
        if (!stateFile.isFile()) {
            return;
        }
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(stateFile.toPath()), StandardCharsets.UTF_8));
            working.copyFrom(fromJson(o.getJSONObject("working")));
            activeId = o.optString("active", ID_CUSTOM);
            // A state file from before this flag existed was written after the user changed something.
            applied = o.optBoolean("applied", true);
            if ((ID_BUILTIN + "bicara").equals(activeId)) {
                // The preset Bicara was replaced by Serba Bisa.
                activeId = SERBA_BISA_ID;
                working.copyFrom(SoundPreset.builtin("serba_bisa"));
            }
            limiter = o.optBoolean("limiter", true);
            smartLoudness = o.optBoolean("smartLoudness", true);
            seat = Math.max(0, Math.min(SEAT_SITE.length - 1, o.optInt("seat", 0)));
            JSONArray arr = o.optJSONArray("users");
            if (arr != null) {
                for (int i = 0; i < arr.length() && users.size() < MAX_USER_PRESETS; i++) {
                    users.add(fromJson(arr.getJSONObject(i)));
                }
            }
            hasSavedState = true;
        } catch (IOException | JSONException e) {
            hasSavedState = false;
        }
    }
}
