package dev.wndrxz.potioncombine.api.events;

import dev.wndrxz.potioncombine.recipe.Recipe;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired right before a finished (or spoiled) potion is handed to the
 * player. Cancellable — useful for cooldowns, region checks, or just
 * eating the click outright.
 */
public final class BrewCollectEvent extends PotionCombineEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Recipe recipe;
    private final ItemStack item;
    private final boolean spoiled;
    private boolean cancelled;

    public BrewCollectEvent(Block cauldron, Player player, Recipe recipe,
                            ItemStack item, boolean spoiled) {
        super(cauldron);
        this.player = player;
        this.recipe = recipe;
        this.item = item;
        this.spoiled = spoiled;
    }

    public Player player()    { return player; }
    public Recipe recipe()    { return recipe; }
    public ItemStack item()   { return item; }
    public boolean spoiled()  { return spoiled; }

    @Override public boolean isCancelled()        { return cancelled; }
    @Override public void setCancelled(boolean c) { this.cancelled = c; }

    @Override public HandlerList getHandlers()  { return HANDLERS; }
    public  static HandlerList getHandlerList() { return HANDLERS; }
}
