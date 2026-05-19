package dev.wndrxz.potioncombine.api.events;

import dev.wndrxz.potioncombine.recipe.Recipe;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired the moment a cauldron's contents match a recipe and the brewing
 * loop is about to start. Cancelling drops the matched recipe back to the
 * collecting state — ingredients stay in the cauldron, no fail effects.
 */
public final class BrewStartEvent extends PotionCombineEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Recipe recipe;
    private boolean cancelled;

    public BrewStartEvent(Block cauldron, Recipe recipe) {
        super(cauldron);
        this.recipe = recipe;
    }

    public Recipe recipe() { return recipe; }

    @Override public boolean isCancelled()        { return cancelled; }
    @Override public void setCancelled(boolean c) { this.cancelled = c; }

    @Override public HandlerList getHandlers()   { return HANDLERS; }
    public  static HandlerList getHandlerList()  { return HANDLERS; }
}
