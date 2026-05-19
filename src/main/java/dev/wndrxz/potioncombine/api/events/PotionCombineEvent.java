package dev.wndrxz.potioncombine.api.events;

import org.bukkit.block.Block;
import org.bukkit.event.Event;

/**
 * Common base for every event the plugin fires. Holds the cauldron block
 * the event refers to so listeners do not have to reach into the source.
 */
public abstract class PotionCombineEvent extends Event {

    private final Block cauldron;

    protected PotionCombineEvent(Block cauldron) {
        this.cauldron = cauldron;
    }

    public Block cauldron() { return cauldron; }
}
