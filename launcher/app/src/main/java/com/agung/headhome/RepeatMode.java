package com.agung.headhome;

/** Repeat modes of the player. The button cycles CONTINUE, REPEAT_ALL, REPEAT_FOLDER, REPEAT_ONE. */
public enum RepeatMode {
    CONTINUE, REPEAT_ALL, REPEAT_FOLDER, REPEAT_ONE;

    public RepeatMode cycle() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RepeatMode parse(String name, RepeatMode fallback) {
        try {
            return name == null ? fallback : valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
