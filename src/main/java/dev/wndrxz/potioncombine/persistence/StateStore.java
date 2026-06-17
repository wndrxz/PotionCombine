package dev.wndrxz.potioncombine.persistence;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.cauldron.CauldronSession;
import dev.wndrxz.potioncombine.util.BlockKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Flat-file persistence for things 1.0 used to drop on a hard crash.
 *
 * Stored:
 *  - pollution levels per cauldron (resilient to chunk unload, no entities involved);
 *  - legacy session entries from older builds, restored once as world drops.
 *
 * Not stored: live BrewingTask state, display entity ids, transient timers.
 */
public final class StateStore {

    private final PotionCombine plugin;
    private final File file;

    public StateStore(PotionCombine plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
    }

    public void save() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            YamlConfiguration out = new YamlConfiguration();

            // Pollution map.
            ConfigurationSection pollution = out.createSection("pollution");
            int polEntries = 0;
            for (var e : plugin.pollutionManager().snapshot().entrySet()) {
                BlockKey k = e.getKey();
                ConfigurationSection s = pollution.createSection("e" + polEntries++);
                s.set("world", k.worldId().toString());
                s.set("x", k.x());
                s.set("y", k.y());
                s.set("z", k.z());
                s.set("level", e.getValue());
            }

            // Cauldron sessions — only worth storing if they hold ingredients.
            ConfigurationSection sessions = out.createSection("sessions");
            int sesEntries = 0;
            for (var entry : plugin.cauldronManager().all().entrySet()) {
                CauldronSession ses = entry.getValue();
                if (ses.isEmpty()) continue;
                BlockKey k = entry.getKey();
                ConfigurationSection s = sessions.createSection("s" + sesEntries++);
                s.set("world", k.worldId().toString());
                s.set("x", k.x());
                s.set("y", k.y());
                s.set("z", k.z());
                List<ItemStack> snap = ses.ingredientsSnapshot();
                // ingredientsSnapshot returns top-of-stack first; when we
                // reload we want to push them in reverse so LIFO order is
                // preserved.
                s.set("ingredients", snap);
            }

            out.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save state.yml: " + ex.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration in = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection pollution = in.getConfigurationSection("pollution");
        if (pollution != null) {
            for (String key : pollution.getKeys(false)) {
                ConfigurationSection s = pollution.getConfigurationSection(key);
                if (s == null) continue;
                BlockKey k = readKey(s);
                if (k == null) continue;
                int level = Math.max(0, s.getInt("level", 0));
                if (level > 0) plugin.pollutionManager().restore(k, level);
            }
        }

        ConfigurationSection sessions = in.getConfigurationSection("sessions");
        List<String> droppedSessionKeys = new ArrayList<>();
        if (sessions != null) {
            for (String key : sessions.getKeys(false)) {
                ConfigurationSection s = sessions.getConfigurationSection(key);
                if (s == null) continue;
                BlockKey k = readKey(s);
                if (k == null) continue;
                World world = plugin.getServer().getWorld(k.worldId());
                if (world == null) continue;
                List<?> raw = s.getList("ingredients");
                if (raw == null || raw.isEmpty()) continue;

                // Drop ingredients on top of the cauldron — players will
                // recover them naturally. Cleaner than trying to recreate
                // a partial session that may or may not still match a recipe.
                org.bukkit.Location at = k.toCenter(world).add(0, 0.7, 0);
                List<ItemStack> stacks = new ArrayList<>();
                for (Object o : raw) {
                    if (o instanceof ItemStack is) stacks.add(is);
                }
                boolean dropped = false;
                for (ItemStack is : stacks) {
                    if (is != null && is.getType() != org.bukkit.Material.AIR) {
                        world.dropItem(at, is);
                        dropped = true;
                    }
                }
                if (dropped) droppedSessionKeys.add(key);
            }
        }

        if (!droppedSessionKeys.isEmpty()) {
            for (String key : droppedSessionKeys) sessions.set(key, null);
            try {
                in.save(file);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Could not prune restored sessions: " + ex.getMessage());
            }
        }
    }

    private static BlockKey readKey(ConfigurationSection s) {
        String w = s.getString("world");
        if (w == null) return null;
        try {
            return new BlockKey(UUID.fromString(w), s.getInt("x"), s.getInt("y"), s.getInt("z"));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
