package dev.wndrxz.potioncombine.brewing;

import dev.wndrxz.potioncombine.PotionCombine;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Optional automation: a vanilla hopper directly under the cauldron pulls
 * the finished potion automatically once the configured grace delay has
 * passed. If the hopper is full or absent the potion just hovers as
 * normal and a player can collect it by hand. Spoiled brews are never
 * pulled — the operator most likely wants to see why a cauldron is
 * spoiling.
 */
public final class HopperExtractor {

    private final PotionCombine plugin;

    public HopperExtractor(PotionCombine plugin) {
        this.plugin = plugin;
    }

    /** True if the configured hopper extract feature is enabled at all. */
    public boolean enabled() {
        return plugin.configManager().hopperExtractEnabled();
    }

    /** Try to push the result item into the hopper below. Returns true when
     *  the item was fully consumed by the hopper. */
    public boolean tryExtract(World world, int cauldronX, int cauldronY, int cauldronZ, ItemStack result) {
        if (!enabled() || world == null || result == null || result.getType() == Material.AIR) return false;
        Block below = world.getBlockAt(cauldronX, cauldronY - 1, cauldronZ);
        if (below.getType() != Material.HOPPER) return false;
        if (!(below.getState() instanceof Hopper hopper)) return false;

        Inventory inv = hopper.getInventory();
        ItemStack copy = result.clone();
        if (!canFit(inv, copy)) return false;

        var leftover = inv.addItem(copy);
        // addItem returns a map of unfit items. Empty map = success.
        return leftover.isEmpty();
    }

    private boolean canFit(Inventory inv, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack slot : inv.getStorageContents()) {
            int max = maxStackSize(inv, item, slot);
            if (slot == null || slot.getType() == Material.AIR) {
                remaining -= max;
            } else if (slot.isSimilar(item) && slot.getAmount() < max) {
                remaining -= max - slot.getAmount();
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static int maxStackSize(Inventory inv, ItemStack item, ItemStack slot) {
        int max = Math.min(inv.getMaxStackSize(), item.getMaxStackSize());
        if (slot != null && slot.getType() != Material.AIR) {
            max = Math.min(max, slot.getMaxStackSize());
        }
        return Math.max(1, max);
    }
}
