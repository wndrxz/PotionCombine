package dev.wndrxz.potioncombine.api.events;

import dev.wndrxz.potioncombine.recipe.Recipe;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a finished brew is about to be poured into an adjacent
 * cauldron as an ingredient instead of hovering for a player. The
 * {@link #cauldron()} block is the source — the one that just finished —
 * and {@link #target()} is the neighbour receiving the result.
 *
 * Cancelling keeps the result where it is: it hovers over the source
 * cauldron and a player collects it by hand, exactly as if no adjacent
 * cauldron had wanted it.
 */
public final class BrewChainEvent extends PotionCombineEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block target;
    private final Recipe recipe;
    private final ItemStack result;
    private boolean cancelled;

    public BrewChainEvent(Block source, Block target, Recipe recipe, ItemStack result) {
        super(source);
        this.target = target;
        this.recipe = recipe;
        this.result = result;
    }

    /** The cauldron the result is being poured into. */
    public Block target() { return target; }

    /** The recipe that just finished in the source cauldron. */
    public Recipe recipe() { return recipe; }

    /** The result item about to be fed downstream. Read-only — decorate the
     *  upstream item through {@link BrewSuccessEvent} instead. */
    public ItemStack result() { return result; }

    @Override public boolean isCancelled()        { return cancelled; }
    @Override public void setCancelled(boolean c) { this.cancelled = c; }

    @Override public HandlerList getHandlers()   { return HANDLERS; }
    public  static HandlerList getHandlerList()  { return HANDLERS; }
}
