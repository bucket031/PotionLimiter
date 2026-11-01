package com.example.potionguard;

import java.util.Locale;

/** What happens to an effect when a potion tries to give it. */
public enum Mode {

    ALLOWED("Allowed"),   // nothing changes
    LIMITED("Limited"),   // a lower level and a shorter duration
    BANNED("Banned");     // never given

    private final String label;

    Mode(String label) {
        this.label = label;
    }

    public String label() { return label; }

    /** Returns null for anything that isn't a mode name. */
    public static Mode fromInput(String input) {
        if (input == null) return null;
        String wanted = input.toUpperCase(Locale.ROOT);
        for (Mode mode : values()) {
            if (mode.name().equals(wanted)) return mode;
        }
        return null;
    }
}
