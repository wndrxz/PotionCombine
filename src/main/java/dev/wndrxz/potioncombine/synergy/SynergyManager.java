package dev.wndrxz.potioncombine.synergy;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.api.events.BrewChainEvent;
import dev.wndrxz.potioncombine.cauldron.CauldronSession;
import dev.wndrxz.potioncombine.locale.LocaleManager;
import dev.wndrxz.potioncombine.recipe.IngredientKey;
import dev.wndrxz.potioncombine.recipe.ItemMatcher;
import dev.wndrxz.potioncombine.recipe.Recipe;
import dev.wndrxz.potioncombine.util.BlockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Multi-cauldron synergy: when a brew finishes, its result can pour straight
 * into an adjacent cauldron that has been pre-loaded with the rest of a
 * higher-tier recipe, instead of hovering for a player to carry by hand.
 *
 * The rule is deliberately strict — a result is only ever fed into a
 * neighbour it would <em>complete</em>. That keeps a valuable bottle from
 * being dropped into a half-finished neighbour and failing into sludge on
 * the next grace tick. The "wait for the upstream brew" half of the dance
 * lives in {@code BrewingService} — a downstream cauldron that is short
 * exactly the upstream's output holds instead of failing while a neighbour
 * is still brewing.
 *
 * Horizontal neighbours only.
 */
public final class SynergyManager {

    // +x, -x, +z, -z — the four cardinal neighbours at the cauldron's own height.
    private static final int[][] OFFSETS = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };

    private final PotionCombine plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public SynergyManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.configManager().synergyEnabled();
    }

    /**
     * Try to pour a finished result into an adjacent cauldron that it would
     * complete. Returns true when the result was handed off — the caller
     * should then collapse the source session exactly as it does for a
     * hopper extract. False means nothing took it and the result should
     * hover as usual.
     */
    public boolean feed(World world, int x, int y, int z, Recipe finished, ItemStack result) {
        if (!enabled() || world == null || result == null || result.getType() == Material.AIR) return false;
        IngredientKey key = plugin.itemMatcher().keyOf(result);
        if (key == null) return false;

        Block target = null;
        for (int[] off : OFFSETS) {
            Block nb = world.getBlockAt(x + off[0], y, z + off[1]);
            if (nb.getType() != Material.WATER_CAULDRON) continue;
            BlockKey nk = BlockKey.of(nb);
            if (plugin.pollutionManager().isBlocked(nk)) continue;
            CauldronSession s = plugin.cauldronManager().get(nk);
            if (s != null && s.state() != CauldronSession.State.COLLECTING) continue;
            if (!wouldComplete(s, key)) continue;
            target = nb;
            break;
        }
        if (target == null) return false;

        Block source = world.getBlockAt(x, y, z);
        BrewChainEvent ev = new BrewChainEvent(source, target, finished, result);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;

        if (target.getType() != Material.WATER_CAULDRON) return false;
        BlockKey targetKey = BlockKey.of(target);
        if (plugin.pollutionManager().isBlocked(targetKey)) return false;

        CauldronSession ts = plugin.cauldronManager().get(targetKey);
        // re-check after the event: handlers can mutate the world under us
        if (ts != null && ts.state() != CauldronSession.State.COLLECTING) return false;
        if (!wouldComplete(ts, key)) return false;
        if (ts == null) ts = plugin.cauldronManager().getOrCreate(target);

        ItemStack feedItem = result.clone();
        feedItem.setAmount(1);
        ts.addIngredient(feedItem);
        ts.lastInsertTick(plugin.getServer().getCurrentTick());

        Location centre = target.getLocation().toCenterLocation();
        plugin.configManager().particles().play("insert", world, centre.clone().add(0, 0.4, 0));
        plugin.soundManager().playAt(centre, plugin.configManager().soundInsert(),
                SoundCategory.BLOCKS, 0.7f, 1.1f);
        Component name = legacy.deserialize(finished.displayNameLegacy());
        plugin.locale().broadcastNearby(centre, plugin.configManager().notifyRadius(),
                "brew.chained", LocaleManager.component("recipe", name));

        // Hand the freshly-poured ingredient to the same evaluation path a
        // manual insert uses. No adder — the cooldown belongs to whoever
        // started the chain, not to the chain itself.
        plugin.brewingService().onIngredientAdded(ts, null);
        return true;
    }

    public boolean hasBrewingNeighborThatCompletes(BlockKey key, Map<IngredientKey, Integer> counts) {
        if (!enabled() || key == null || counts == null || counts.isEmpty()) return false;
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) return false;
        for (int[] off : OFFSETS) {
            Block nb = world.getBlockAt(key.x() + off[0], key.y(), key.z() + off[1]);
            if (nb.getType() != Material.WATER_CAULDRON) continue;
            CauldronSession s = plugin.cauldronManager().get(BlockKey.of(nb));
            if (s == null || s.state() != CauldronSession.State.BREWING || s.matched() == null) continue;

            ItemStack result = plugin.recipeManager().produce(s.matched());
            IngredientKey resultKey = plugin.itemMatcher().keyOf(result);
            if (resultKey != null && wouldComplete(counts, resultKey)) return true;
        }
        return false;
    }

    /** Would adding one of {@code key} turn this collecting cauldron into an
     *  exact recipe match? A neighbour that already matches something on its
     *  own is left alone — that brew starts without our help. */
    private boolean wouldComplete(CauldronSession s, IngredientKey key) {
        ItemMatcher matcher = plugin.itemMatcher();
        Map<IngredientKey, Integer> counts = (s != null && !s.isEmpty())
                ? matcher.countItems(s.ingredientsSnapshot())
                : new HashMap<>();
        return wouldComplete(counts, key);
    }

    private boolean wouldComplete(Map<IngredientKey, Integer> counts, IngredientKey key) {
        if (key == null) return false;
        ItemMatcher matcher = plugin.itemMatcher();
        if (!counts.isEmpty()
                && matcher.matchKeys(counts, plugin.recipeManager().all()).result == ItemMatcher.Result.MATCH) {
            return false;
        }
        Map<IngredientKey, Integer> augmented = new HashMap<>(counts);
        augmented.merge(key, 1, Integer::sum);
        return matcher.matchKeys(augmented, plugin.recipeManager().all()).result == ItemMatcher.Result.MATCH;
    }
}
