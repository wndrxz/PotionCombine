package dev.wndrxz.potioncombine.cauldron;

import dev.wndrxz.potioncombine.recipe.Recipe;
import dev.wndrxz.potioncombine.util.BlockKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class CauldronSession {

    public enum State { COLLECTING, BREWING, READY, SPOILED }

    private final BlockKey location;
    private final Deque<ItemStack> ingredients = new ArrayDeque<>();
    private State state = State.COLLECTING;
    private Recipe matched;
    private ItemStack readyItem;

    private BukkitTask graceTask;
    private BukkitTask brewTask;
    private BukkitTask spoilTask;
    private UUID textDisplayId;
    private UUID itemDisplayId;
    private long lastInsertTick;
    private double progressFraction; // 0.0 at brew start, 1.0 at finish
    private long holdingSinceMillis; // 0 = not holding for an upstream brew
    private int brewTotalTicks;      // adjusted brew length, kept so a resumed brew lands at the same finish
    private int readyElapsedTicks;   // ticks the finished result has hovered, for the spoil clock across a restart

    public CauldronSession(BlockKey location) {
        this.location = location;
    }

    public BlockKey location() { return location; }
    public State state() { return state; }
    public void state(State s) { this.state = s; }

    /** Push to top of the LIFO stack. */
    public void addIngredient(ItemStack item) { ingredients.push(item); }
    /** Pop the most-recently-added item, or null if empty. */
    public ItemStack popIngredient() { return ingredients.isEmpty() ? null : ingredients.pop(); }
    public boolean isEmpty() { return ingredients.isEmpty(); }
    public int ingredientCount() { return ingredients.size(); }

    /** Snapshot view for the matcher. Top-of-stack first. */
    public List<ItemStack> ingredientsSnapshot() {
        return new ArrayList<>(ingredients);
    }

    public Recipe matched() { return matched; }
    public void matched(Recipe r) { this.matched = r; }

    public ItemStack readyItem() { return readyItem; }
    public void readyItem(ItemStack s) { this.readyItem = s; }

    public BukkitTask graceTask() { return graceTask; }
    public void graceTask(BukkitTask t) { this.graceTask = t; }

    public BukkitTask brewTask() { return brewTask; }
    public void brewTask(BukkitTask t) { this.brewTask = t; }

    public BukkitTask spoilTask() { return spoilTask; }
    public void spoilTask(BukkitTask t) { this.spoilTask = t; }

    public UUID textDisplayId() { return textDisplayId; }
    public void textDisplayId(UUID id) { this.textDisplayId = id; }

    public UUID itemDisplayId() { return itemDisplayId; }
    public void itemDisplayId(UUID id) { this.itemDisplayId = id; }

    public long lastInsertTick() { return lastInsertTick; }
    public void lastInsertTick(long t) { this.lastInsertTick = t; }

    public double progressFraction() { return progressFraction; }
    public void progressFraction(double f) { this.progressFraction = f; }

    public long holdingSinceMillis() { return holdingSinceMillis; }
    public void holdingSinceMillis(long t) { this.holdingSinceMillis = t; }

    public int brewTotalTicks() { return brewTotalTicks; }
    public void brewTotalTicks(int t) { this.brewTotalTicks = t; }

    public int readyElapsedTicks() { return readyElapsedTicks; }
    public void readyElapsedTicks(int t) { this.readyElapsedTicks = t; }
}
