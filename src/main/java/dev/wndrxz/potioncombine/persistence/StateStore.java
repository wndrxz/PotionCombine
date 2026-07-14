package dev.wndrxz.potioncombine.persistence;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.cauldron.CauldronSession;
import dev.wndrxz.potioncombine.recipe.Recipe;
import dev.wndrxz.potioncombine.util.BlockKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Flat-file persistence for the things that should outlive a shutdown.
 *
 * Stored:
 *  - pollution levels per cauldron (resilient to chunk unload, no entities involved);
 *  - live brews (1.3): a cauldron mid-BREWING or holding a READY/SPOILED result
 *    is written with enough state — recipe, progress, spoil clock — to be picked
 *    back up on the next boot, closing the "a hard crash loses the brew" edge
 *    that 1.0–1.2 carried.
 *
 * Read but never written any more: legacy "sessions" entries from older
 * builds. A still-collecting cauldron's loose ingredients are dropped into
 * the world by onDisable, so writing them here as well meant the next boot
 * dropped a second copy of everything (the 1.3.0 duplication bug). Old files
 * that still carry a sessions block get it restored once as drops, then
 * pruned.
 *
 * Not stored: transient bukkit task handles or display-entity ids; those are
 * rebuilt by the resumed brew loop. A resumed brew keeps the exact ingredients
 * that were inside it, so cancelling it later still drops the right things.
 */
public final class StateStore {

    private final PotionCombine plugin;
    private final File file;

    public StateStore(PotionCombine plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
    }

    /**
     * Persist state and return the keys of every cauldron written as a
     * resumable live brew. The caller drops every other session's items onto
     * the ground, but must skip these — they will be restored, not
     * re-dropped, so spilling them here would duplicate the items.
     */
    public Set<BlockKey> save() {
        Set<BlockKey> persistedBrews = new HashSet<>();
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            YamlConfiguration out = new YamlConfiguration();

            // Pollution map.
            ConfigurationSection pollution = out.createSection("pollution");
            int polEntries = 0;
            for (var e : plugin.pollutionManager().snapshot().entrySet()) {
                BlockKey k = e.getKey();
                ConfigurationSection s = pollution.createSection("e" + polEntries++);
                writeKey(s, k);
                s.set("level", e.getValue());
            }

            boolean resumeBrews = plugin.configManager().restoreLiveBrews();

            // Live brews (and finished-but-uncollected results) are written
            // for resume. Still-collecting cauldrons are deliberately NOT
            // written: onDisable drops their ingredients into the world, and
            // saving them here too would hand the player a second copy on
            // the next boot.
            ConfigurationSection brews = out.createSection("brews");
            int brewEntries = 0;
            for (var entry : plugin.cauldronManager().all().entrySet()) {
                CauldronSession ses = entry.getValue();
                BlockKey k = entry.getKey();

                boolean live = ses.state() == CauldronSession.State.BREWING
                        || ses.state() == CauldronSession.State.READY
                        || ses.state() == CauldronSession.State.SPOILED;
                if (!resumeBrews || !live || ses.matched() == null) continue;

                ConfigurationSection s = brews.createSection("b" + brewEntries++);
                writeKey(s, k);
                s.set("state", ses.state().name());
                s.set("recipe", ses.matched().id());
                s.set("progress", ses.progressFraction());
                s.set("brew_ticks", ses.brewTotalTicks());
                s.set("ready_elapsed_ticks", ses.readyElapsedTicks());
                if (ses.readyItem() != null) s.set("ready_item", ses.readyItem());
                s.set("ingredients", ses.ingredientsSnapshot());
                persistedBrews.add(k);
            }

            out.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save state.yml: " + ex.getMessage());
        }
        return persistedBrews;
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

        boolean dirty = restoreLiveBrews(in);
        if (dropLegacySessions(in)) dirty = true;

        // Prune whatever was just consumed. Without this, a hard crash
        // before the next clean shutdown would replay the same brews and
        // drops — free potions on every dirty reboot.
        if (dirty) {
            try {
                in.save(file);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not prune state.yml after restore: " + ex.getMessage());
            }
        }
    }

    /** Hand each persisted live brew back to the brewing service so its loop
     *  starts again from where it left off. A brew whose recipe no longer
     *  loads, or whose cauldron has been emptied of water in the meantime,
     *  falls back to dropping its ingredients rather than vanishing. Entries
     *  for worlds that are not loaded right now are kept for a later boot;
     *  everything else is consumed here and must not fire twice. Returns
     *  true when the file needs a re-save. */
    private boolean restoreLiveBrews(YamlConfiguration in) {
        ConfigurationSection brews = in.getConfigurationSection("brews");
        if (brews == null) return false;
        int resumed = 0;
        List<String> consumed = new ArrayList<>();
        for (String key : brews.getKeys(false)) {
            ConfigurationSection s = brews.getConfigurationSection(key);
            if (s == null) continue;
            BlockKey k = readKey(s);
            if (k == null) {
                consumed.add(key);
                continue;
            }
            World world = plugin.getServer().getWorld(k.worldId());
            if (world == null) continue;

            Recipe recipe = plugin.recipeManager().get(s.getString("recipe"));
            List<ItemStack> ingredients = new ArrayList<>();
            List<?> raw = s.getList("ingredients");
            if (raw != null) {
                for (Object o : raw) if (o instanceof ItemStack is) ingredients.add(is);
            }

            CauldronSession.State state;
            try {
                state = CauldronSession.State.valueOf(s.getString("state", "BREWING"));
            } catch (IllegalArgumentException ex) {
                state = CauldronSession.State.BREWING;
            }

            double progress = s.getDouble("progress", 0.0);
            int brewTicks = s.getInt("brew_ticks", 0);
            int readyElapsed = s.getInt("ready_elapsed_ticks", 0);
            ItemStack readyItem = s.getItemStack("ready_item");

            boolean ok = plugin.brewingService().resumeBrew(
                    k, recipe, state, progress, brewTicks, readyElapsed, readyItem, ingredients);
            if (ok) resumed++;
            // Resumed, or resumeBrew already dropped its items — either way
            // this entry is spent.
            consumed.add(key);
        }
        for (String key : consumed) brews.set(key, null);
        if (resumed > 0) plugin.getLogger().info("Resumed " + resumed + " live brew(s) from state.yml.");
        return !consumed.isEmpty();
    }

    /** Legacy "sessions" block written by builds before 1.3.1: the loose
     *  ingredients of still-collecting cauldrons. Restored once as drops on
     *  top of their cauldron, then pruned. Returns true when the file needs
     *  a re-save. */
    private boolean dropLegacySessions(YamlConfiguration in) {
        ConfigurationSection sessions = in.getConfigurationSection("sessions");
        if (sessions == null) return false;
        List<String> droppedSessionKeys = new ArrayList<>();
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
            boolean dropped = false;
            for (Object o : raw) {
                if (o instanceof ItemStack is && is.getType() != org.bukkit.Material.AIR) {
                    world.dropItem(at, is);
                    dropped = true;
                }
            }
            if (dropped) droppedSessionKeys.add(key);
        }
        for (String key : droppedSessionKeys) sessions.set(key, null);
        return !droppedSessionKeys.isEmpty();
    }

    private static void writeKey(ConfigurationSection s, BlockKey k) {
        s.set("world", k.worldId().toString());
        s.set("x", k.x());
        s.set("y", k.y());
        s.set("z", k.z());
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
