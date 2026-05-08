package dev.wndrxz.potioncombine.cauldron;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.util.BlockKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public final class CauldronManager {

    private final PotionCombine plugin;
    private final Map<BlockKey, CauldronSession> sessions = new HashMap<>();

    public CauldronManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public CauldronSession getOrCreate(Block block) {
        return sessions.computeIfAbsent(BlockKey.of(block), CauldronSession::new);
    }

    public CauldronSession get(Block block) {
        return sessions.get(BlockKey.of(block));
    }

    public CauldronSession get(BlockKey key) {
        return sessions.get(key);
    }

    public void remove(BlockKey key) {
        sessions.remove(key);
    }

    public Map<BlockKey, CauldronSession> all() { return sessions; }

    public boolean isWaterCauldron(Block block) {
        return block.getType() == Material.WATER_CAULDRON;
    }

    /** Resolve a CauldronSession at this exact location, even if we no longer
     *  have one — used by tasks that captured a location before disable. */
    public Location centerOf(BlockKey key, World world) {
        return key.toCenter(world);
    }

    /** Cancel a pending grace task on this session, if any. */
    public void cancelGrace(CauldronSession s) {
        BukkitTask t = s.graceTask();
        if (t != null) {
            t.cancel();
            s.graceTask(null);
        }
    }

    /** Cancel an in-flight brew on this session, if any. */
    public void cancelBrew(CauldronSession s) {
        BukkitTask t = s.brewTask();
        if (t != null) {
            t.cancel();
            s.brewTask(null);
        }
    }

    /** Cancel a pending spoil timer, if any. */
    public void cancelSpoil(CauldronSession s) {
        BukkitTask t = s.spoilTask();
        if (t != null) {
            t.cancel();
            s.spoilTask(null);
        }
    }

    public void cancelAllTasks(CauldronSession s) {
        cancelGrace(s);
        cancelBrew(s);
        cancelSpoil(s);
    }

    /** Drop every ingredient currently held by every session at its cauldron.
     *  Called on plugin disable. */
    public void dropAllIngredients(World fallback) {
        for (Map.Entry<BlockKey, CauldronSession> e : sessions.entrySet()) {
            CauldronSession s = e.getValue();
            BlockKey k = e.getKey();
            World w = plugin.getServer().getWorld(k.worldId());
            World use = w != null ? w : fallback;
            if (use == null) continue;
            Location at = k.toCenter(use);
            ItemStack ing;
            while ((ing = s.popIngredient()) != null) {
                use.dropItem(at, ing);
            }
        }
    }
}
