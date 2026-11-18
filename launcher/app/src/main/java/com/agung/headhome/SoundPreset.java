package com.agung.headhome;

/** A sound setting: five simple controls and a per-band offset for the 32-band mode. Pure data. */
public final class SoundPreset {

    public static final int BANDS = 32;
    public static final String[] STYLE_KEYS = {"flat_atas", "nendang", "vokal", "datar", "malam"};
    public static final String[] GENRE_KEYS = {"koplo", "dangdut", "akustik", "rock", "jazz", "dj_edm", "pop", "bicara"};
    public static final String[] BUILTIN_KEYS = {"flat_atas", "nendang", "vokal", "datar", "malam", "koplo", "dangdut",
            "akustik", "rock", "jazz", "dj_edm", "pop", "bicara"};

    public String name = "";
    public int level;
    public int bass;
    public int clarity;
    public int vocal;
    public int treble;
    public int[] offs = new int[BANDS];

    public SoundPreset() {
    }

    public SoundPreset(String name, int level, int bass, int clarity, int vocal, int treble) {
        this.name = name;
        this.level = level;
        this.bass = bass;
        this.clarity = clarity;
        this.vocal = vocal;
        this.treble = treble;
    }

    public SoundPreset copy() {
        SoundPreset p = new SoundPreset(name, level, bass, clarity, vocal, treble);
        p.offs = offs.clone();
        return p;
    }

    public void copyFrom(SoundPreset o) {
        level = o.level;
        bass = o.bass;
        clarity = o.clarity;
        vocal = o.vocal;
        treble = o.treble;
        offs = o.offs.clone();
    }

    /** The built-in presets from the design. Returns null for an unknown key. */
    public static SoundPreset builtin(String key) {
        switch (key) {
            case "flat_atas":
                return new SoundPreset(key, 8, 0, 0, 0, 0);
            case "nendang":
                return new SoundPreset(key, 5, 3, 1, 1, 2);
            case "vokal":
                return new SoundPreset(key, 5, 1, 1, 3, 1);
            case "datar":
                return new SoundPreset(key, 0, 0, 0, 0, 0);
            case "malam":
                return new SoundPreset(key, 2, 2, 1, 1, -2);
            case "koplo":
                return new SoundPreset(key, 5, 4, 2, 3, 2);
            case "dangdut":
                return new SoundPreset(key, 5, 3, 2, 3, 1);
            case "akustik":
                return new SoundPreset(key, 5, 1, 2, 2, 3);
            case "rock":
                return new SoundPreset(key, 5, 4, 2, 0, 4);
            case "jazz":
                return new SoundPreset(key, 4, 2, 1, 1, 2);
            case "dj_edm":
                return new SoundPreset(key, 4, 5, 2, 0, 4);
            case "pop":
                return new SoundPreset(key, 5, 2, 1, 2, 2);
            case "bicara":
                return new SoundPreset(key, 4, -2, 2, 4, 0);
            default:
                return null;
        }
    }
}
