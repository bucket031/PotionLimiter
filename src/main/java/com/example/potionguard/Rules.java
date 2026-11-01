package com.example.potionguard;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reads and writes the per-effect rules stored in config.yml. */
public final class Rules {

    /** The ways a potion effect can reach a creature. */
    public enum Source {
        DRINKING("drinking", "Drinking potions", Material.POTION),
        SPLASHED("splashed", "Splash potions", Material.SPLASH_POTION),
        LINGERING("lingering", "Lingering clouds", Material.LINGERING_POTION),
        ARROWS("arrows", "Tipped arrows", Material.TIPPED_ARROW);

        private final String id;
        private final String label;
        private final Material icon;

        Source(String id, String label, Material icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }

        public String id() { return id; }
        public String label() { return label; }
        public Material icon() { return icon; }
    }

    public static final int DEFAULT_MAX_LEVEL = 1;
    public static final int DEFAULT_PERCENT = 50;
    public static final int LEVEL_CAP = 255;

    private final PotionGuard plugin;

    public Rules(PotionGuard plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    // -------------------------------------------------------------- effects

    public static String key(PotionEffectType type) {
        return type.getKey().getKey();
    }

    /** Every effect in the game, sorted by name. */
    public List<PotionEffectType> all() {
        List<PotionEffectType> list = new ArrayList<>();
        for (PotionEffectType type : Registry.EFFECT) {
            list.add(type);
        }
        list.sort(Comparator.comparing(Rules::key));
        return list;
    }

    /** Finds an effect by name ("strength", "minecraft:strength", "instant-health"). Null if unknown. */
    public PotionEffectType find(String input) {
        String wanted = input.toLowerCase(Locale.ROOT).replace("minecraft:", "")
                .replace('-', '_').replace(' ', '_');
        for (PotionEffectType type : Registry.EFFECT) {
            if (key(type).equals(wanted)) return type;
        }
        return null;
    }

    // ---------------------------------------------------------------- rules

    private static String path(PotionEffectType type, String field) {
        return "effects." + key(type) + "." + field;
    }

    public Mode mode(PotionEffectType type) {
        Mode mode = Mode.fromInput(config().getString(path(type, "mode"), "ALLOWED"));
        return mode == null ? Mode.ALLOWED : mode;
    }

    public void setMode(PotionEffectType type, Mode mode) {
        config().set(path(type, "mode"), mode.name());
        plugin.saveConfig();
    }

    public int maxLevel(PotionEffectType type) {
        int level = config().getInt(path(type, "max_level"), DEFAULT_MAX_LEVEL);
        return Math.max(1, Math.min(LEVEL_CAP, level));
    }

    public void setMaxLevel(PotionEffectType type, int level) {
        config().set(path(type, "max_level"), Math.max(1, Math.min(LEVEL_CAP, level)));
        plugin.saveConfig();
    }

    public int durationPercent(PotionEffectType type) {
        int percent = config().getInt(path(type, "duration_percent"), DEFAULT_PERCENT);
        return Math.max(0, Math.min(100, percent));
    }

    public void setDurationPercent(PotionEffectType type, int percent) {
        config().set(path(type, "duration_percent"), Math.max(0, Math.min(100, percent)));
        plugin.saveConfig();
    }

    // -------------------------------------------------------------- sources

    public boolean applies(Source source) {
        return config().getBoolean("apply_to." + source.id(), true);
    }

    public void setApplies(Source source, boolean value) {
        config().set("apply_to." + source.id(), value);
        plugin.saveConfig();
    }

    // ------------------------------------------------------------- adjusting

    /**
     * What a potion effect becomes under the rules.
     * Returns null if it is banned, the same object if nothing changes,
     * or a weaker copy if it is limited.
     */
    public PotionEffect adjust(PotionEffect effect) {
        PotionEffectType type = effect.getType();
        Mode mode = mode(type);
        if (mode == Mode.BANNED) return null;
        if (mode == Mode.ALLOWED) return effect;

        int amplifier = Math.min(effect.getAmplifier(), maxLevel(type) - 1);
        int duration = effect.getDuration();
        if (duration > 0 && !type.isInstant()) {
            duration = (int) Math.max(1L, (long) duration * durationPercent(type) / 100L);
        }
        if (amplifier == effect.getAmplifier() && duration == effect.getDuration()) return effect;

        return new PotionEffect(type, duration, amplifier,
                effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
    }

    /**
     * How strong an instant effect (healing, harming) stays, from 0 to 1.
     * Each level doubles the strength, so a level above the limit is scaled down by half per level.
     */
    public double instantScale(PotionEffectType type, int amplifier) {
        Mode mode = mode(type);
        if (mode == Mode.BANNED) return 0.0;
        if (mode == Mode.ALLOWED) return 1.0;
        int over = amplifier - (maxLevel(type) - 1);
        return over <= 0 ? 1.0 : Math.pow(0.5, over);
    }
}
