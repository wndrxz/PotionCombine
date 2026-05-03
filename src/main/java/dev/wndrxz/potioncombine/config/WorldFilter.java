package dev.wndrxz.potioncombine.config;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Optional per-world gate for the whole plugin. Two modes — whitelist (only
 * the listed worlds get brewing) and blacklist (every world except the
 * listed ones). When the section is missing or empty, every world is in.
 */
public final class WorldFilter {

    public enum Mode { WHITELIST, BLACKLIST }

    private Mode mode = Mode.BLACKLIST;
    private final Set<String> names = new HashSet<>();

    public void load(ConfigurationSection section) {
        names.clear();
        mode = Mode.BLACKLIST;
        if (section == null) return;

        String raw = section.getString("mode", "blacklist");
        try {
            mode = Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            mode = Mode.BLACKLIST;
        }
        List<String> list = section.getStringList("list");
        for (String n : list) {
            if (n != null && !n.isBlank()) names.add(n.toLowerCase(Locale.ROOT));
        }
    }

    public boolean allows(World world) {
        if (world == null) return false;
        if (names.isEmpty()) return true;
        boolean listed = names.contains(world.getName().toLowerCase(Locale.ROOT));
        return mode == Mode.WHITELIST ? listed : !listed;
    }

    public Mode mode() { return mode; }
}
