package dev.wndrxz.potioncombine.integration;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.util.BlockKey;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Real PAPI expansion. Loaded only when PAPI is on the server (handled by
 * {@link PlaceholderHook} — the class is referenced inside that hook, not
 * during plugin construction, so a missing PAPI does not blow up class
 * loading).
 */
public final class PotionCombineExpansion extends PlaceholderExpansion {

    private final PotionCombine plugin;

    public PotionCombineExpansion(PotionCombine plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "potioncombine"; }
    @Override public @NotNull String getAuthor()     { return "wndrxz"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onRequest(OfflinePlayer who, @NotNull String params) {
        String p = params.toLowerCase();
        switch (p) {
            case "active_brews": {
                int count = 0;
                for (var s : plugin.cauldronManager().all().values()) {
                    if (s.state() == dev.wndrxz.potioncombine.cauldron.CauldronSession.State.BREWING) count++;
                }
                return Integer.toString(count);
            }
            case "recipe_count":
                return Integer.toString(plugin.recipeManager().all().size());
            case "cooldown":
                return who == null ? "0" :
                        Integer.toString(plugin.cooldownManager().remainingSeconds(who.getUniqueId()));
            case "cooldown_ready":
                return (who == null || plugin.cooldownManager().remainingMillis(who.getUniqueId()) <= 0)
                        ? "yes" : "no";
            case "pollution_here": {
                if (!(who instanceof Player player)) return "0";
                Block b = player.getLocation().getBlock();
                return Integer.toString(plugin.pollutionManager().level(BlockKey.of(b)));
            }
            default:
                return null;
        }
    }
}
