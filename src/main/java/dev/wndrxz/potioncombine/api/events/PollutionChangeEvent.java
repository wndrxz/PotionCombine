package dev.wndrxz.potioncombine.api.events;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired whenever a cauldron's pollution level moves up or down. Cancelling
 * leaves the level untouched — handy for region plugins that want certain
 * areas to stay permanently clean.
 */
public final class PollutionChangeEvent extends PotionCombineEvent implements Cancellable {

    public enum Cause { BREW_SUCCESS, BREW_FAIL, BREW_SPOIL, BRUSH, WATER_REFILL, EXTERNAL }

    private static final HandlerList HANDLERS = new HandlerList();

    private final int oldLevel;
    private final int newLevel;
    private final Cause cause;
    private boolean cancelled;

    public PollutionChangeEvent(Block cauldron, int oldLevel, int newLevel, Cause cause) {
        super(cauldron);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.cause = cause;
    }

    public int oldLevel() { return oldLevel; }
    public int newLevel() { return newLevel; }
    public int delta()    { return newLevel - oldLevel; }
    public Cause cause()  { return cause; }

    @Override public boolean isCancelled()        { return cancelled; }
    @Override public void setCancelled(boolean c) { this.cancelled = c; }

    @Override public HandlerList getHandlers()  { return HANDLERS; }
    public  static HandlerList getHandlerList() { return HANDLERS; }
}
