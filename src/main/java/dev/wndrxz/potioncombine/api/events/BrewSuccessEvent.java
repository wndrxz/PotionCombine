package dev.wndrxz.potioncombine.api.events;

import dev.wndrxz.potioncombine.recipe.Recipe;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a brew finishes and the result item is about to hover over
 * the cauldron. Mutate the result via {@link #result(ItemStack)} if you
 * want to swap or decorate it.
 */
public final class BrewSuccessEvent extends PotionCombineEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Recipe recipe;
    private ItemStack result;

    public BrewSuccessEvent(Block cauldron, Recipe recipe, ItemStack result) {
        super(cauldron);
        this.recipe = recipe;
        this.result = result;
    }

    public Recipe recipe()         { return recipe; }
    public ItemStack result()      { return result; }
    public void result(ItemStack r) { this.result = r; }

    @Override public HandlerList getHandlers()  { return HANDLERS; }
    public  static HandlerList getHandlerList() { return HANDLERS; }
}
