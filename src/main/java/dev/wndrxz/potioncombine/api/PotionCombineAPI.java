package dev.wndrxz.potioncombine.api;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.recipe.Recipe;
import dev.wndrxz.potioncombine.util.BlockKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Static access for other plugins. Stable surface; everything inside this
 * class is what we promise not to break across patch versions of 1.x.
 *
 * Usage from another plugin:
 * <pre>
 *   if (Bukkit.getPluginManager().getPlugin("PotionCombine") != null) {
 *       int dirt = PotionCombineAPI.pollution(myCauldron);
 *       PotionCombineAPI.give(player, "phoenix_elixir", 1);
 *   }
 * </pre>
 *
 * Listen to {@link dev.wndrxz.potioncombine.api.events.BrewStartEvent} and
 * friends for the event side of the API.
 */
public final class PotionCombineAPI {

    private static PotionCombine plugin;

    private PotionCombineAPI() {}

    public static void bind(PotionCombine instance) { plugin = instance; }

    /** True if the plugin has finished its onEnable. */
    public static boolean ready() { return plugin != null && plugin.isEnabled(); }

    /** Look up a loaded recipe by id. Null if no such recipe is loaded. */
    public static Recipe recipe(String id) {
        if (!ready() || id == null) return null;
        return plugin.recipeManager().get(id);
    }

    /** Read-only view of every recipe loaded from recipes.yml. */
    public static Collection<Recipe> recipes() {
        if (!ready()) return java.util.List.of();
        return plugin.recipeManager().all();
    }

    /** Build a finished result item for a recipe id (clone, safe to mutate).
     *  Null when no such recipe exists. */
    public static ItemStack produce(String recipeId) {
        if (!ready()) return null;
        return plugin.recipeManager().produce(recipeId);
    }

    /** Hand a player a finished custom potion. Overflow falls at their feet. */
    public static int give(Player who, String recipeId, int amount) {
        if (!ready() || who == null || recipeId == null || amount <= 0) return 0;
        ItemStack template = plugin.recipeManager().produce(recipeId);
        if (template == null) return 0;
        int given = 0;
        for (int i = 0; i < amount; i++) {
            var overflow = who.getInventory().addItem(template.clone());
            for (ItemStack o : overflow.values()) who.getWorld().dropItem(who.getLocation(), o);
            given++;
        }
        return given;
    }

    /** Current pollution level at a cauldron (0 if clean or pollution disabled). */
    public static int pollution(Block cauldron) {
        if (!ready() || cauldron == null) return 0;
        return plugin.pollutionManager().level(BlockKey.of(cauldron));
    }

    /** Pollution threshold above which a cauldron refuses inserts. */
    public static int pollutionThreshold() {
        return ready() ? plugin.pollutionManager().threshold() : 0;
    }

    /** Imperatively change pollution. Fires PollutionChangeEvent (cancellable).
     *  Returns the new level after the change. */
    public static int setPollution(Block cauldron, int level) {
        if (!ready() || cauldron == null) return 0;
        return plugin.pollutionManager().setLevel(BlockKey.of(cauldron), Math.max(0, level));
    }

    /** Active brewing sessions on a given cauldron — handy for "is something
     *  cooking here right now" checks. */
    public static boolean isBrewing(Block cauldron) {
        if (!ready() || cauldron == null) return false;
        var s = plugin.cauldronManager().get(cauldron);
        return s != null && s.state() == dev.wndrxz.potioncombine.cauldron.CauldronSession.State.BREWING;
    }

    /** Remaining cooldown in milliseconds for a player; 0 when none. */
    public static long cooldownRemaining(UUID playerId) {
        if (!ready() || playerId == null) return 0L;
        return plugin.cooldownManager().remainingMillis(playerId);
    }

    public static long cooldownRemaining(Player player) {
        return cooldownRemaining(Objects.requireNonNull(player).getUniqueId());
    }

    /** True if multi-cauldron synergy (brew chaining + the downstream hold)
     *  is switched on in config. */
    public static boolean synergyEnabled() {
        return ready() && plugin.configManager().synergyEnabled();
    }
}
