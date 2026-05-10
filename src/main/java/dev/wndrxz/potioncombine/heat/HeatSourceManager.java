package dev.wndrxz.potioncombine.heat;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.util.BlockKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Heat sources sit directly under a cauldron and tilt the brewing
 * mechanic in the player's favour: faster brews and a softer pollution
 * spoil-roll. Defined entirely in config — the plugin maps a block type
 * to a brew-time multiplier and a pollution-resist factor.
 *
 * Lit-state-aware: a campfire that has been put out does not count.
 */
public final class HeatSourceManager {

    public record Source(double brewTimeMultiplier, double pollutionResist) {
        public static final Source NONE = new Source(1.0, 0.0);
        public boolean isNone() { return brewTimeMultiplier == 1.0 && pollutionResist == 0.0; }
    }

    private final PotionCombine plugin;
    private final Map<Material, Source> sources = new HashMap<>();
    private boolean enabled = true;
    private boolean requireLit = true;

    public HeatSourceManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public void load(org.bukkit.configuration.ConfigurationSection section) {
        sources.clear();
        if (section == null) {
            enabled = true;
            requireLit = true;
            applyDefaults();
            return;
        }
        enabled = section.getBoolean("enabled", true);
        requireLit = section.getBoolean("require_lit", true);

        org.bukkit.configuration.ConfigurationSection list = section.getConfigurationSection("sources");
        if (list == null) {
            applyDefaults();
            return;
        }
        for (String key : list.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection entry = list.getConfigurationSection(key);
            if (entry == null) continue;
            Material mat;
            try {
                mat = Material.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Heat source '" + key + "' is not a known material, skipped.");
                continue;
            }
            double mul = clampPositive(entry.getDouble("brew_time_multiplier", 1.0), 1.0);
            double res = clamp01(entry.getDouble("pollution_resist", 0.0));
            sources.put(mat, new Source(mul, res));
        }
        if (sources.isEmpty()) applyDefaults();
    }

    /** Block directly below the cauldron — that is the only slot we check. */
    public Source resolve(BlockKey cauldron) {
        if (!enabled || cauldron == null) return Source.NONE;
        World world = plugin.getServer().getWorld(cauldron.worldId());
        if (world == null) return Source.NONE;
        Block below = world.getBlockAt(cauldron.x(), cauldron.y() - 1, cauldron.z());
        Source s = sources.get(below.getType());
        if (s == null) return Source.NONE;

        if (requireLit && below.getBlockData() instanceof Lightable lit && !lit.isLit()) {
            return Source.NONE;
        }
        return s;
    }

    public boolean enabled() { return enabled; }

    private void applyDefaults() {
        // A modest, sane default set. Operators will tune to taste.
        sources.put(Material.CAMPFIRE,      new Source(0.85, 0.0));
        sources.put(Material.SOUL_CAMPFIRE, new Source(0.75, 0.20));
        sources.put(Material.MAGMA_BLOCK,   new Source(0.90, 0.0));
        sources.put(Material.FIRE,          new Source(0.85, 0.0));
        // SOUL_FIRE is restricted in 1.21 in some forms; guarded lookup.
        Material soulFire = matchOrNull("SOUL_FIRE");
        if (soulFire != null) sources.put(soulFire, new Source(0.75, 0.25));
        sources.put(Material.LAVA,          new Source(0.70, 0.30));
    }

    private static Material matchOrNull(String name) {
        try { return Material.valueOf(name); } catch (IllegalArgumentException ex) { return null; }
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static double clampPositive(double v, double def) {
        if (Double.isNaN(v) || v <= 0.0) return def;
        if (v > 4.0) return 4.0;
        return v;
    }
}
