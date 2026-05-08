package dev.wndrxz.potioncombine.util;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;

public final class VersionUtil {

    private VersionUtil() {}

    private static final int[] MC = parse(Bukkit.getBukkitVersion());

    public static int major() { return MC[0]; }
    public static int minor() { return MC[1]; }
    public static int patch() { return MC[2]; }

    /** True when the running server is at least the given minor (e.g. atLeast(21, 0)). */
    public static boolean atLeast(int minor, int patch) {
        if (MC[0] > 1) return true;
        if (MC[1] != minor) return MC[1] > minor;
        return MC[2] >= patch;
    }

    /** Look up a Particle by name; null if it does not exist on this server. */
    public static Particle particleOrNull(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Look up a Sound enum by name; null if missing. Note: code paths that
     *  accept a String key should NOT go through this — pass the raw string
     *  to playSound(Location, String, ...) so resource pack keys still work. */
    public static Sound soundOrNull(String name) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int[] parse(String bukkitVersion) {
        // Bukkit version looks like "1.20.4-R0.1-SNAPSHOT".
        try {
            String head = bukkitVersion.split("-", 2)[0];
            String[] parts = head.split("\\.");
            int a = parts.length > 0 ? Integer.parseInt(parts[0]) : 1;
            int b = parts.length > 1 ? Integer.parseInt(parts[1]) : 20;
            int c = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new int[] { a, b, c };
        } catch (Exception ex) {
            return new int[] { 1, 20, 0 };
        }
    }
}
