package dev.wndrxz.potioncombine.progress;

import dev.wndrxz.potioncombine.PotionCombine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Alchemist progression: discoveries plus the counters the journal's lab-notes
 * page reads. Storage is the same flat-file approach the pollution state uses,
 * kept behind this one manager so an SQLite backend can replace progress.yml
 * later without touching callers. No bundled JDBC driver (no-shading rule),
 * hence flat-file for now.
 *
 * With {@code progression.enabled: false} nothing is tracked, no file is
 * written, and the journal command politely says so.
 */
public final class ProgressManager {

    private final PotionCombine plugin;
    private final File file;
    private final Map<UUID, PlayerProgress> byPlayer = new HashMap<>();
    private boolean dirty;
    private boolean loaded;

    public ProgressManager(PotionCombine plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "progress.yml");
    }

    public boolean enabled() {
        return plugin.configManager().progressionEnabled();
    }

    /** The progress record for a player, created on first touch. Never null
     *  while progression is enabled; returns an empty throwaway when it is
     *  off so read paths (the journal) don't have to special-case null. */
    public PlayerProgress of(UUID playerId) {
        if (playerId == null) return new PlayerProgress();
        if (!enabled()) return byPlayer.getOrDefault(playerId, new PlayerProgress());
        return byPlayer.computeIfAbsent(playerId, k -> new PlayerProgress());
    }

    /** Record a finished brew and mark the recipe discovered. Returns true
     *  when this is the first time the player has ever brewed this recipe —
     *  the caller uses it to fire the one-shot discovery message. */
    public boolean recordBrew(UUID playerId, String recipeId) {
        if (!enabled() || playerId == null) return false;
        PlayerProgress p = of(playerId);
        boolean firstTime = p.discover(recipeId);
        p.recordBrew(recipeId);
        dirty = true;
        return firstTime;
    }

    public void recordSpoiled(UUID playerId) {
        if (!enabled() || playerId == null) return;
        of(playerId).recordSpoiled();
        dirty = true;
    }

    public void recordFailed(UUID playerId) {
        if (!enabled() || playerId == null) return;
        of(playerId).recordFailed();
        dirty = true;
    }

    /** True once {@link #load()} has read the file in this server session.
     *  Lets a reload that flips progression on pull the data in without a
     *  full restart, while a reload that finds it already loaded leaves the
     *  in-memory tallies untouched. */
    public boolean loaded() { return loaded; }

    public void load() {
        byPlayer.clear();
        dirty = false;
        if (!enabled()) {
            // Leave 'loaded' false so a later reload that turns progression on
            // gets a chance to read the file in.
            return;
        }
        loaded = true;
        if (!file.exists()) return;

        YamlConfiguration in = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = in.getConfigurationSection("players");
        if (players == null) return;

        for (String raw : players.getKeys(false)) {
            ConfigurationSection s = players.getConfigurationSection(raw);
            if (s == null) continue;
            UUID id;
            try {
                id = UUID.fromString(raw);
            } catch (IllegalArgumentException ex) {
                continue; // a hand-edited key that isn't a UUID — skip it quietly
            }
            PlayerProgress p = new PlayerProgress();
            for (String rid : s.getStringList("discovered")) p.restoreDiscovered(rid);
            ConfigurationSection counts = s.getConfigurationSection("brews");
            if (counts != null) {
                for (String rid : counts.getKeys(false)) p.restoreBrewCount(rid, counts.getInt(rid, 0));
            }
            p.restoreTotals(s.getInt("total_brews", 0),
                    s.getInt("total_spoiled", 0),
                    s.getInt("total_failed", 0));
            if (!p.isEmpty()) byPlayer.put(id, p);
        }
    }

    public void save() {
        if (!enabled() || !dirty) return;
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            YamlConfiguration out = new YamlConfiguration();
            ConfigurationSection players = out.createSection("players");
            for (Map.Entry<UUID, PlayerProgress> e : byPlayer.entrySet()) {
                PlayerProgress p = e.getValue();
                if (p.isEmpty()) continue;
                ConfigurationSection s = players.createSection(e.getKey().toString());
                s.set("discovered", new java.util.ArrayList<>(p.discovered()));
                if (!p.brewCounts().isEmpty()) {
                    ConfigurationSection counts = s.createSection("brews");
                    for (Map.Entry<String, Integer> c : p.brewCounts().entrySet()) {
                        counts.set(c.getKey(), c.getValue());
                    }
                }
                s.set("total_brews",   p.totalBrews());
                s.set("total_spoiled", p.totalSpoiled());
                s.set("total_failed",  p.totalFailed());
            }
            out.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save progress.yml: " + ex.getMessage());
        }
    }
}
