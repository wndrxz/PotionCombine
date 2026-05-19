package dev.wndrxz.potioncombine.integration;

import dev.wndrxz.potioncombine.PotionCombine;
import org.bukkit.plugin.Plugin;

/**
 * Lightweight wrapper around the PAPI hook so the rest of the plugin does
 * not have to care whether PlaceholderAPI is present. Calling
 * {@link #tryRegister()} when PAPI is absent simply returns false and logs
 * nothing — the integration is genuinely optional.
 */
public final class PlaceholderHook {

    private final PotionCombine plugin;
    private boolean registered;

    public PlaceholderHook(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public boolean isRegistered() { return registered; }

    public boolean tryRegister() {
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) return false;
        try {
            // The class is loaded here, not at plugin construction time, so
            // a server without PAPI never resolves the import inside the
            // expansion class.
            new PotionCombineExpansion(plugin).register();
            registered = true;
            plugin.getLogger().info("PlaceholderAPI expansion registered.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not register PAPI expansion: " + t.getMessage());
            return false;
        }
    }
}
