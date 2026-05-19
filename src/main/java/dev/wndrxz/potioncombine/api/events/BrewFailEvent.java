package dev.wndrxz.potioncombine.api.events;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;

/**
 * Fired when a brew fails. Not cancellable: by the time we know it failed,
 * ingredients have already been turned into sludge.
 */
public final class BrewFailEvent extends PotionCombineEvent {

    public enum Reason { DEAD_END, INCOMPLETE, WATER_LOST, POLLUTED }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Reason reason;

    public BrewFailEvent(Block cauldron, Reason reason) {
        super(cauldron);
        this.reason = reason;
    }

    public Reason reason() { return reason; }

    @Override public HandlerList getHandlers()  { return HANDLERS; }
    public  static HandlerList getHandlerList() { return HANDLERS; }
}
