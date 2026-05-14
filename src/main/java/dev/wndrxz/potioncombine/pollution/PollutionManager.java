package dev.wndrxz.potioncombine.pollution;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.api.events.PollutionChangeEvent;
import dev.wndrxz.potioncombine.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PollutionManager {

    private final PotionCombine plugin;
    private final Map<BlockKey, Integer> levels = new HashMap<>();
    private final Map<BlockKey, BukkitTask> idleTasks = new HashMap<>();

    public PollutionManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.configManager().pollutionEnabled();
    }

    public int level(BlockKey key) {
        if (key == null) return 0;
        Integer v = levels.get(key);
        return v == null ? 0 : v;
    }

    public int threshold() {
        return plugin.configManager().pollutionThreshold();
    }

    public boolean isBlocked(BlockKey key) {
        return enabled() && level(key) >= threshold();
    }

    public void add(BlockKey key, int amount, PollutionChangeEvent.Cause cause) {
        if (!enabled() || key == null || amount <= 0) return;
        int before = level(key);
        int next = Math.min(threshold(), before + amount);
        if (next == before) return;
        if (!fireChange(key, before, next, cause)) return;
        levels.put(key, next);
        refreshIdle(key, next);
    }

    /** Backwards-compatible overload — used by older callers. Treats every
     *  add as EXTERNAL so the event still fires with a sensible cause. */
    public void add(BlockKey key, int amount) {
        add(key, amount, PollutionChangeEvent.Cause.EXTERNAL);
    }

    public void reduce(BlockKey key, int amount, PollutionChangeEvent.Cause cause) {
        if (key == null || amount <= 0) return;
        int before = level(key);
        if (before == 0) return;
        int next = Math.max(0, before - amount);
        if (next == before) return;
        if (!fireChange(key, before, next, cause)) return;
        if (next <= 0) levels.remove(key);
        else           levels.put(key, next);
        refreshIdle(key, next);
    }

    public void reduce(BlockKey key, int amount) {
        reduce(key, amount, PollutionChangeEvent.Cause.EXTERNAL);
    }

    public void clear(BlockKey key, PollutionChangeEvent.Cause cause) {
        if (key == null) return;
        int before = level(key);
        if (before == 0) {
            cancelIdle(key);
            return;
        }
        if (!fireChange(key, before, 0, cause)) return;
        levels.remove(key);
        cancelIdle(key);
    }

    public void clear(BlockKey key) {
        clear(key, PollutionChangeEvent.Cause.EXTERNAL);
    }

    /** Set the absolute level. Returns the level after the (possibly cancelled)
     *  change. */
    public int setLevel(BlockKey key, int level) {
        if (key == null) return 0;
        int target = Math.max(0, Math.min(threshold(), level));
        int before = level(key);
        if (target == before) return before;
        if (!fireChange(key, before, target, PollutionChangeEvent.Cause.EXTERNAL)) return before;
        if (target <= 0) levels.remove(key);
        else             levels.put(key, target);
        refreshIdle(key, target);
        return target;
    }

    /** Spoil-chance from accumulated pollution. Clamped to [0, 0.95]. */
    public double spoilChance(BlockKey key) {
        if (!enabled()) return 0.0;
        double v = level(key) * plugin.configManager().pollutionSpoilChancePerPoint();
        if (v < 0.0) return 0.0;
        if (v > 0.95) return 0.95;
        return v;
    }

    /** Multiplier on brew time. 1.0 when clean, grows with pollution. */
    public double brewTimeMultiplier(BlockKey key) {
        if (!enabled()) return 1.0;
        double factor = plugin.configManager().pollutionBrewTimeFactorPerPoint();
        if (factor <= 0.0) return 1.0;
        return 1.0 + factor * level(key);
    }

    /** Restore a level on plugin enable from persisted state — bypasses the
     *  cancellable change event so a region plugin's "always clean" rule does
     *  not fight the loader. */
    public void restore(BlockKey key, int level) {
        if (key == null || level <= 0 || !enabled()) return;
        int clamped = Math.min(threshold(), level);
        levels.put(key, clamped);
        refreshIdle(key, clamped);
    }

    public Map<BlockKey, Integer> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(levels));
    }

    public void shutdown() {
        for (BukkitTask t : idleTasks.values()) {
            if (t != null) t.cancel();
        }
        idleTasks.clear();
        levels.clear();
    }

    private boolean fireChange(BlockKey key, int before, int after, PollutionChangeEvent.Cause cause) {
        World w = plugin.getServer().getWorld(key.worldId());
        if (w == null) return true;
        Block block = w.getBlockAt(key.x(), key.y(), key.z());
        PollutionChangeEvent ev = new PollutionChangeEvent(block, before, after, cause);
        Bukkit.getPluginManager().callEvent(ev);
        return !ev.isCancelled();
    }

    private void refreshIdle(BlockKey key, int level) {
        cancelIdle(key);
        if (!enabled() || level <= 0) return;
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) return;
        Location at = key.toCenter(world).add(0, 0.3, 0);
        // Idle ambience: a faint smoke pulse whose rate scales with the
        // level. Stops when the level hits zero or the chunk unloads (the
        // particle call itself is a no-op if no players are nearby).
        int period = Math.max(20, 80 - level * 4);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World w = plugin.getServer().getWorld(key.worldId());
            if (w == null) return;
            if (level(key) <= 0) {
                cancelIdle(key);
                return;
            }
            // Block was broken or replaced — drop the stale level so the
            // next time someone places a cauldron here they start clean,
            // and stop pumping particles at empty air.
            org.bukkit.Material here = w.getBlockAt(key.x(), key.y(), key.z()).getType();
            if (here != org.bukkit.Material.WATER_CAULDRON && here != org.bukkit.Material.CAULDRON) {
                clear(key, PollutionChangeEvent.Cause.EXTERNAL);
                return;
            }
            plugin.configManager().particles().play("pollution_idle", w, at);
            plugin.soundManager().idle(at);
        }, period, period);
        idleTasks.put(key, task);
    }

    private void cancelIdle(BlockKey key) {
        BukkitTask t = idleTasks.remove(key);
        if (t != null) t.cancel();
    }
}
