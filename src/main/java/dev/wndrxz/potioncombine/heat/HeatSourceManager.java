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

    // Area heat: when off, only the block directly below counts (1.1
    // behaviour). When on, the strongest source within `areaRadius` blocks
    // (same layer and the layer below) contributes, weakened by distance.
    private boolean areaEnabled = false;
    private int areaRadius = 2;
    private double areaFalloff = 0.34;

    public HeatSourceManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public void load(org.bukkit.configuration.ConfigurationSection section) {
        sources.clear();
        if (section == null) {
            enabled = true;
            requireLit = true;
            areaEnabled = false;
            areaRadius = 2;
            areaFalloff = 0.34;
            applyDefaults();
            return;
        }
        enabled = section.getBoolean("enabled", true);
        requireLit = section.getBoolean("require_lit", true);

        org.bukkit.configuration.ConfigurationSection area = section.getConfigurationSection("area");
        if (area == null) {
            areaEnabled = false;
            areaRadius = 2;
            areaFalloff = 0.34;
        } else {
            areaEnabled = area.getBoolean("enabled", false);
            areaRadius = Math.max(1, Math.min(4, area.getInt("radius", 2)));
            areaFalloff = clamp01(area.getDouble("falloff_per_block", 0.34));
        }

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

    /** The heat acting on a cauldron. With area heat off this is just the
     *  block directly below; with it on, the strongest qualifying source in
     *  range wins, scaled down the further it sits from the cauldron. */
    public Source resolve(BlockKey cauldron) {
        if (!enabled || cauldron == null) return Source.NONE;
        World world = plugin.getServer().getWorld(cauldron.worldId());
        if (world == null) return Source.NONE;

        if (!areaEnabled) {
            return sourceAt(world, cauldron.x(), cauldron.y() - 1, cauldron.z());
        }

        // Scan a box around and below the cauldron, keep whichever source
        // gives the biggest brew-time win after distance fall-off. Two
        // layers — the cauldron's own and the one below — so a ring of lava
        // around the base counts, not just the single block under it.
        Source best = Source.NONE;
        double bestSpeedup = 0.0;
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -areaRadius; dx <= areaRadius; dx++) {
                for (int dz = -areaRadius; dz <= areaRadius; dz++) {
                    int dist = Math.max(Math.abs(dx), Math.abs(dz)) + (dy == 0 ? 1 : 0);
                    if (dist > areaRadius) continue;
                    Source raw = sourceAt(world, cauldron.x() + dx, cauldron.y() + dy, cauldron.z() + dz);
                    if (raw.isNone()) continue;
                    Source scaled = scaleByDistance(raw, dist, areaFalloff);
                    double speedup = 1.0 - scaled.brewTimeMultiplier();
                    if (speedup > bestSpeedup) {
                        bestSpeedup = speedup;
                        best = scaled;
                    }
                }
            }
        }
        return best;
    }

    /** Resolve a single block into a heat source, honouring require_lit. */
    private Source sourceAt(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Source s = sources.get(block.getType());
        if (s == null) return Source.NONE;
        if (requireLit && block.getBlockData() instanceof Lightable lit && !lit.isLit()) {
            return Source.NONE;
        }
        return s;
    }

    /** Weaken a source by its distance from the cauldron. The speedup (how
     *  far the multiplier sits below 1.0) and the pollution-resist both fade
     *  by {@code falloff} per block; an adjacent block keeps its full value. */
    static Source scaleByDistance(Source raw, int distance, double falloff) {
        if (distance <= 1 || raw.isNone()) return raw;
        double factor = 1.0 - falloff * (distance - 1);
        if (factor <= 0.0) return Source.NONE;
        double speedup = (1.0 - raw.brewTimeMultiplier()) * factor;
        double mul = 1.0 - speedup;
        double resist = raw.pollutionResist() * factor;
        return new Source(mul, resist);
    }

    public boolean enabled() { return enabled; }

    private void applyDefaults() {
        // defaults, operators will tune to taste
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
