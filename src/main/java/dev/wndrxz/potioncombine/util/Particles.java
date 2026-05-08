package dev.wndrxz.potioncombine.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Particle resolution that survives the 1.20 → 1.21 enum renames
 * (REDSTONE → DUST, VILLAGER_HAPPY → HAPPY_VILLAGER, ...). The Particle
 * references are looked up reflectively once and cached; spawning calls
 * fall through silently if the particle simply does not exist on the host.
 */
public final class Particles {

    public static final Particle WITCH      = first("WITCH");
    public static final Particle BUBBLE_POP = first("BUBBLE_POP");
    public static final Particle HAPPY      = first("HAPPY_VILLAGER", "VILLAGER_HAPPY");
    public static final Particle DUST       = first("DUST", "REDSTONE");
    public static final Particle LARGE_SMOKE = first("LARGE_SMOKE", "SMOKE_LARGE");
    public static final Particle POOF       = first("POOF", "EXPLOSION_NORMAL");

    private Particles() {}

    private static Particle first(String... names) {
        for (String n : names) {
            try { return Particle.valueOf(n); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    public static void spawn(World w, Particle p, Location at, int count,
                             double dx, double dy, double dz, double speed) {
        if (p == null || w == null) return;
        w.spawnParticle(p, at, count, dx, dy, dz, speed);
    }

    public static void spawnDust(World w, Location at, int count, Color color, float size) {
        if (DUST == null || w == null) return;
        w.spawnParticle(DUST, at, count, 0.3, 0.3, 0.3, 0.0,
                new Particle.DustOptions(color, size));
    }
}
