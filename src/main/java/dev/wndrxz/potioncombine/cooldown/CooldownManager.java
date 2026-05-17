package dev.wndrxz.potioncombine.cooldown;

import dev.wndrxz.potioncombine.PotionCombine;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player throttle on starting new brews. Players with the bypass perm
 * walk past it. Memory only — cooldowns are intentionally session-scoped
 * (a player who logs out and back in waits no longer than they would have).
 */
public final class CooldownManager {

    private final PotionCombine plugin;
    private final Map<UUID, Long> nextAllowed = new HashMap<>();

    public CooldownManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    /** Bypass permission overrides every cooldown. */
    public boolean canBrew(Player player) {
        if (player == null) return true;
        if (player.hasPermission("potioncombine.cooldown.bypass")) return true;
        long until = nextAllowed.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() >= until;
    }

    /** Stamp the cooldown for the player, using the configured duration. */
    public void touch(Player player) {
        if (player == null) return;
        long secs = plugin.configManager().cooldownSeconds();
        if (secs <= 0) return;
        if (player.hasPermission("potioncombine.cooldown.bypass")) return;
        nextAllowed.put(player.getUniqueId(), System.currentTimeMillis() + secs * 1000L);
    }

    /** Remaining cooldown in milliseconds; 0 when none. */
    public long remainingMillis(UUID playerId) {
        if (playerId == null) return 0L;
        long until = nextAllowed.getOrDefault(playerId, 0L);
        long left = until - System.currentTimeMillis();
        return left > 0 ? left : 0L;
    }

    /** Remaining cooldown in whole seconds, rounded up. */
    public int remainingSeconds(UUID playerId) {
        long ms = remainingMillis(playerId);
        if (ms <= 0) return 0;
        return (int) ((ms + 999L) / 1000L);
    }

    public void clear() {
        nextAllowed.clear();
    }
}
