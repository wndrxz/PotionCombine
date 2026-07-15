package dev.wndrxz.potioncombine.brewing;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.api.events.BrewCollectEvent;
import dev.wndrxz.potioncombine.api.events.BrewFailEvent;
import dev.wndrxz.potioncombine.api.events.BrewStartEvent;
import dev.wndrxz.potioncombine.api.events.PollutionChangeEvent;
import dev.wndrxz.potioncombine.cauldron.CauldronSession;
import dev.wndrxz.potioncombine.config.ConfigManager;
import dev.wndrxz.potioncombine.heat.HeatSourceManager;
import dev.wndrxz.potioncombine.locale.LocaleManager;
import dev.wndrxz.potioncombine.recipe.IngredientKey;
import dev.wndrxz.potioncombine.recipe.ItemMatcher;
import dev.wndrxz.potioncombine.recipe.Recipe;
import dev.wndrxz.potioncombine.util.BlockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

public final class BrewingService {

    public enum FailureReason { DEAD_END, INCOMPLETE, WATER_LOST, POLLUTED }

    private final PotionCombine plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public BrewingService(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public void onIngredientAdded(CauldronSession session, Player adder) {
        session.holdingSinceMillis(0L); // new ingredient = progress, reset the upstream-wait timer
        ItemMatcher matcher = plugin.itemMatcher();
        Map<IngredientKey, Integer> counts = matcher.countItems(session.ingredientsSnapshot());
        ItemMatcher.MatchResult res = matcher.matchKeys(counts, plugin.recipeManager().all());
        if (res.result == ItemMatcher.Result.NO_MATCH) {
            failBrew(session, FailureReason.DEAD_END, adder);
            return;
        }

        plugin.cauldronManager().cancelGrace(session);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> evaluateAfterGrace(session, adder),
                plugin.configManager().gracePeriodTicks());
        session.graceTask(task);
    }

    private void evaluateAfterGrace(CauldronSession session, Player adder) {
        if (session.state() != CauldronSession.State.COLLECTING) return;

        ItemMatcher matcher = plugin.itemMatcher();
        Map<IngredientKey, Integer> counts = matcher.countItems(session.ingredientsSnapshot());
        ItemMatcher.MatchResult res = matcher.matchKeys(counts, plugin.recipeManager().all());

        switch (res.result) {
            case MATCH    -> startBrew(session, res.matched, adder);
            case PARTIAL  -> {
                if (tryHoldForUpstream(session, counts, adder)) return;
                failBrew(session, FailureReason.INCOMPLETE, adder);
            }
            case NO_MATCH -> failBrew(session, FailureReason.DEAD_END,   adder);
        }
    }

    // A downstream cauldron that's short exactly an upstream brew's output shouldn't
    // fail the instant grace runs out, the upstream may still be cooking. Re-check
    // every grace period up to the configured cap. Returns true while the hold is on.
    private boolean tryHoldForUpstream(CauldronSession session,
                                       Map<IngredientKey, Integer> counts,
                                       Player adder) {
        int maxSeconds = plugin.configManager().synergyMaxHoldSeconds();
        if (maxSeconds <= 0 || counts.isEmpty()) return false;
        if (!plugin.synergyManager().hasBrewingNeighborThatCompletes(session.location(), counts)) {
            session.holdingSinceMillis(0);
            return false;
        }

        long now = System.currentTimeMillis();
        if (session.holdingSinceMillis() == 0L) {
            session.holdingSinceMillis(now);
            World world = plugin.getServer().getWorld(session.location().worldId());
            if (world != null) {
                plugin.locale().broadcastNearby(session.location().toCenter(world),
                        plugin.configManager().notifyRadius(), "brew.waiting");
            }
        } else if (now - session.holdingSinceMillis() >= maxSeconds * 1000L) {
            session.holdingSinceMillis(0);
            return false;
        }

        plugin.cauldronManager().cancelGrace(session);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> evaluateAfterGrace(session, adder),
                plugin.configManager().gracePeriodTicks());
        session.graceTask(task);
        return true;
    }

    public void startBrew(CauldronSession session, Recipe recipe, Player adder) {
        BlockKey key = session.location();
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) return;

        if (plugin.pollutionManager().isBlocked(key)) {
            failBrew(session, FailureReason.POLLUTED, null);
            return;
        }

        // roll the spoil chance up front (heat softens it); failing fast beats
        // failing after the player sat through a whole cycle
        double chance = plugin.pollutionManager().spoilChance(key);
        HeatSourceManager.Source heat = plugin.heatSourceManager().resolve(key);
        if (chance > 0.0 && !heat.isNone()) {
            chance = chance * (1.0 - heat.pollutionResist());
        }
        if (chance > 0.0 && Math.random() < chance) {
            failBrew(session, FailureReason.POLLUTED, null);
            return;
        }

        Block cauldronBlock = world.getBlockAt(key.x(), key.y(), key.z());
        BrewStartEvent startEvent = new BrewStartEvent(cauldronBlock, recipe);
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            // Stay in COLLECTING so the player can keep adding or retrieve.
            return;
        }

        if (adder != null) plugin.cooldownManager().touch(adder);

        session.state(CauldronSession.State.BREWING);
        session.matched(recipe);
        plugin.cauldronManager().cancelGrace(session);

        Location centre = key.toCenter(world);
        Location dispLoc = centre.clone().add(0, plugin.configManager().displayYOffset(), 0);

        Component recipeName = legacy.deserialize(recipe.displayNameLegacy());
        Component header = plugin.locale().get("brew.started",
                LocaleManager.component("recipe", recipeName));
        java.util.UUID textId = plugin.displayManager().spawnText(dispLoc, header);
        session.textDisplayId(textId);

        int spoilTicks = plugin.configManager().overbrewSeconds() * 20;

        double pollutionMul = plugin.pollutionManager().brewTimeMultiplier(key);
        double heatMul      = heat.brewTimeMultiplier();
        int adjustedBrewTicks = (int) Math.max(20, Math.round(
                recipe.brewTimeTicks() * pollutionMul * heatMul));

        BrewingTask task = new BrewingTask(plugin, session, recipe, world, centre,
                adjustedBrewTicks, spoilTicks);
        BukkitTask handle = task.runTaskTimer(plugin, 0L, 1L);
        session.brewTask(handle);

        plugin.configManager().particles().play("brew_start", world, dispLoc);
        plugin.soundManager().bubble(centre);
    }

    public void failBrew(CauldronSession session, FailureReason reason, Player notify) {
        BlockKey key = session.location();
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) {
            plugin.cauldronManager().cancelAllTasks(session);
            plugin.cauldronManager().remove(key);
            return;
        }
        Location centre = key.toCenter(world);

        plugin.cauldronManager().cancelAllTasks(session);

        ItemStack sludgeTemplate = sludgeTemplate();
        ItemStack ing;
        while ((ing = session.popIngredient()) != null) {
            if (sludgeTemplate != null) {
                ItemStack drop = sludgeTemplate.clone();
                drop.setAmount(Math.max(1, ing.getAmount()));
                world.dropItem(centre.clone().add(0, 0.6, 0), drop);
            }
        }

        plugin.displayManager().remove(session.textDisplayId(), world);
        plugin.displayManager().remove(session.itemDisplayId(), world);
        session.textDisplayId(null);
        session.itemDisplayId(null);

        plugin.soundManager().fail(centre);
        plugin.configManager().particles().play("brew_fail", world, centre.clone().add(0, 0.5, 0));

        String key2 = switch (reason) {
            case DEAD_END    -> "brew.failed_dead_end";
            case INCOMPLETE  -> "brew.failed_incomplete";
            case WATER_LOST  -> "brew.failed_water_lost";
            case POLLUTED    -> "brew.failed_polluted";
        };
        if (notify != null && notify.isOnline()) {
            plugin.locale().send(notify, key2);
            // water-loss is nobody's fault; everything else lands in the lab-notes tally
            if (reason != FailureReason.WATER_LOST) {
                plugin.progressManager().recordFailed(notify.getUniqueId());
            }
        }
        // ping bystanders too, minus the player we already messaged directly
        plugin.locale().broadcastNearbyExcept(centre,
                plugin.configManager().notifyRadius(), notify, key2);

        // Failures dirty the cauldron more than successes do. Water-loss is
        // "not your fault" and does not add pollution.
        if (reason != FailureReason.WATER_LOST) {
            plugin.pollutionManager().add(key, plugin.configManager().pollutionPerFail(),
                    PollutionChangeEvent.Cause.BREW_FAIL);
        }

        Block cauldronBlock = world.getBlockAt(key.x(), key.y(), key.z());
        Bukkit.getPluginManager().callEvent(new BrewFailEvent(cauldronBlock, mapReason(reason)));

        plugin.cauldronManager().remove(key);
    }

    private static BrewFailEvent.Reason mapReason(FailureReason r) {
        return switch (r) {
            case DEAD_END   -> BrewFailEvent.Reason.DEAD_END;
            case INCOMPLETE -> BrewFailEvent.Reason.INCOMPLETE;
            case WATER_LOST -> BrewFailEvent.Reason.WATER_LOST;
            case POLLUTED   -> BrewFailEvent.Reason.POLLUTED;
        };
    }

    public void collectReady(CauldronSession session, Player player) {
        if (session.state() != CauldronSession.State.READY
                && session.state() != CauldronSession.State.SPOILED) return;

        BlockKey key = session.location();
        World world = plugin.getServer().getWorld(key.worldId());
        Location centre = world != null ? key.toCenter(world) : player.getLocation();

        ItemStack item = session.readyItem();
        boolean spoiled = session.state() == CauldronSession.State.SPOILED;

        if (world != null) {
            Block cauldronBlock = world.getBlockAt(key.x(), key.y(), key.z());
            BrewCollectEvent ev = new BrewCollectEvent(cauldronBlock, player, session.matched(), item, spoiled);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return;
        }

        if (item != null && item.getType() != Material.AIR) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            if (!overflow.isEmpty()) {
                for (ItemStack o : overflow.values()) player.getWorld().dropItem(player.getLocation(), o);
                plugin.locale().send(player, "cauldron.collect_inventory_full");
            }
        }

        if (spoiled) {
            plugin.locale().send(player, "brew.spoiled");
            plugin.progressManager().recordSpoiled(player.getUniqueId());
        } else {
            Component recipeName = session.matched() != null
                    ? legacy.deserialize(session.matched().displayNameLegacy())
                    : Component.empty();
            plugin.locale().send(player, "cauldron.collect_success",
                    LocaleManager.component("recipe", recipeName));

            // credit goes to whoever collects, not whoever dropped the last ingredient
            String recipeId = session.matched() != null ? session.matched().id() : null;
            boolean firstTime = plugin.progressManager().recordBrew(player.getUniqueId(), recipeId);
            if (firstTime) {
                plugin.locale().send(player, "journal.discovered",
                        LocaleManager.component("recipe", recipeName));
            }
        }

        plugin.cauldronManager().cancelAllTasks(session);
        if (world != null) {
            plugin.displayManager().remove(session.textDisplayId(), world);
            plugin.displayManager().remove(session.itemDisplayId(), world);
            plugin.configManager().particles().play("collect", world, centre.clone().add(0, 0.6, 0));
        }
        plugin.cauldronManager().remove(key);
    }

    public void onWaterLost(CauldronSession session) {
        failBrew(session, FailureReason.WATER_LOST, null);
    }

    /**
     * Re-arm a brew that was persisted before a shutdown. Called once per
     * saved brew on plugin enable. Rebuilds the session, its display entities
     * and (for a still-cooking brew) the brew loop from the progress recorded
     * at save time, so a hard crash mid-brew is no longer a black hole.
     *
     * Returns false — and drops the ingredients back at the cauldron — when
     * the brew can't be honoured: the recipe was removed from recipes.yml, or
     * the water is gone from the block while we were down.
     */
    public boolean resumeBrew(BlockKey key, Recipe recipe, CauldronSession.State state,
                              double progress, int brewTicks, int readyElapsed,
                              ItemStack readyItem, java.util.List<ItemStack> ingredients) {
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) return false;

        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        boolean stillBrewing = state == CauldronSession.State.BREWING;

        // A recipe that no longer loads, or a cauldron whose water has gone,
        // can't carry a live brew. Give the ingredients back rather than keep
        // a half-state the player can never finish.
        if (recipe == null
                || (stillBrewing && block.getType() != Material.WATER_CAULDRON)) {
            dropResumeFallback(world, key, ingredients, readyItem);
            return false;
        }

        CauldronSession session = plugin.cauldronManager().getOrCreate(block);
        session.matched(recipe);
        for (ItemStack ing : reversed(ingredients)) {
            if (ing != null && ing.getType() != Material.AIR) session.addIngredient(ing);
        }

        Location centre = key.toCenter(world);
        Location dispLoc = centre.clone().add(0, plugin.configManager().displayYOffset(), 0);
        int spoilTicks = plugin.configManager().overbrewSeconds() * 20;
        int total = brewTicks > 0 ? brewTicks : recipe.brewTimeTicks();

        if (stillBrewing) {
            session.state(CauldronSession.State.BREWING);
            session.progressFraction(progress);
            int startTicks = (int) Math.max(0, Math.min(total, Math.round(progress * total)));

            Component header = plugin.locale().get("brew.started",
                    LocaleManager.component("recipe",
                            legacy.deserialize(recipe.displayNameLegacy())));
            session.textDisplayId(plugin.displayManager().spawnText(dispLoc, header));

            BrewingTask task = new BrewingTask(plugin, session, recipe, world, centre,
                    total, spoilTicks, startTicks, BrewingTask.Phase.BREWING, 0);
            session.brewTask(task.runTaskTimer(plugin, 0L, 1L));
            return true;
        }

        // READY or SPOILED — the brew is done, the result hovers. Re-spawn the
        // glowing item and either keep counting toward spoil (READY) or leave
        // it parked as spoiled until someone collects it.
        ItemStack hover = readyItem != null ? readyItem : plugin.recipeManager().produce(recipe);
        if (hover == null) hover = new ItemStack(Material.POTION);
        session.readyItem(hover);
        session.progressFraction(1.0);
        session.itemDisplayId(plugin.displayManager().spawnItem(dispLoc, hover));

        if (state == CauldronSession.State.SPOILED) {
            session.state(CauldronSession.State.SPOILED);
            return true;
        }

        session.state(CauldronSession.State.READY);
        session.readyElapsedTicks(readyElapsed);
        BrewingTask task = new BrewingTask(plugin, session, recipe, world, centre,
                total, spoilTicks, total, BrewingTask.Phase.READY, readyElapsed);
        session.brewTask(task.runTaskTimer(plugin, 0L, 1L));
        return true;
    }

    private void dropResumeFallback(World world, BlockKey key,
                                    java.util.List<ItemStack> ingredients, ItemStack readyItem) {
        Location at = key.toCenter(world).add(0, 0.7, 0);
        if (readyItem != null && readyItem.getType() != Material.AIR) {
            world.dropItem(at, readyItem.clone());
            return;
        }
        if (ingredients == null) return;
        for (ItemStack ing : ingredients) {
            if (ing != null && ing.getType() != Material.AIR) world.dropItem(at, ing);
        }
    }

    // persisted list is top-of-stack first; push it back reversed so retrieve keeps LIFO order
    private static java.util.List<ItemStack> reversed(java.util.List<ItemStack> in) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>(in);
        java.util.Collections.reverse(out);
        return out;
    }

    /** Player (or something else) broke the cauldron block. The session is
     *  always destroyed; what falls onto the ground depends on which phase
     *  the brew was in. Pollution at the block is cleared — if a new
     *  cauldron lands there later, it starts fresh. */
    public void onCauldronBroken(CauldronSession session) {
        BlockKey key = session.location();
        World world = plugin.getServer().getWorld(key.worldId());
        if (world == null) {
            plugin.cauldronManager().cancelAllTasks(session);
            plugin.cauldronManager().remove(key);
            return;
        }
        Location centre = key.toCenter(world);
        plugin.cauldronManager().cancelAllTasks(session);

        Block block = world.getBlockAt(key.x(), key.y(), key.z());
        CauldronSession.State state = session.state();

        if (state == CauldronSession.State.READY || state == CauldronSession.State.SPOILED) {
            ItemStack ready = session.readyItem();
            if (ready != null && ready.getType() != Material.AIR) {
                world.dropItem(centre.clone().add(0, 0.6, 0), ready.clone());
            }
        } else if (state == CauldronSession.State.BREWING) {
            double f = session.progressFraction();
            ConfigManager cfg = plugin.configManager();
            if (f < cfg.breakEarlyUntil()) {
                // Early — give the ingredients back, the player barely started.
                dropIngredientsRaw(session, world, centre);
            } else if (f >= cfg.breakLateFrom()) {
                // Late — the brew was almost there. Drop a tagged "unfinished"
                // bottle so the player knows it was close but not done.
                ItemStack near = nearlyDoneBottle();
                if (near != null) world.dropItem(centre.clone().add(0, 0.6, 0), near);
            } else {
                // Middle — too far to recover ingredients, too early for a
                // real bottle. Sludge.
                dropSludgeFor(session, world, centre);
            }
        } else {
            // COLLECTING — always return the ingredients.
            dropIngredientsRaw(session, world, centre);
        }

        plugin.displayManager().remove(session.textDisplayId(), world);
        plugin.displayManager().remove(session.itemDisplayId(), world);

        plugin.pollutionManager().clear(key, dev.wndrxz.potioncombine.api.events.PollutionChangeEvent.Cause.EXTERNAL);

        plugin.soundManager().cauldronBroken(centre);
        plugin.configManager().particles().play("brew_fail", world, centre.clone().add(0, 0.5, 0));
        plugin.locale().broadcastNearby(centre,
                plugin.configManager().notifyRadius(), "brew.cauldron_broken");

        Bukkit.getPluginManager().callEvent(new dev.wndrxz.potioncombine.api.events.BrewFailEvent(
                block, dev.wndrxz.potioncombine.api.events.BrewFailEvent.Reason.WATER_LOST));

        plugin.cauldronManager().remove(key);
    }

    private void dropIngredientsRaw(CauldronSession session, World world, Location centre) {
        ItemStack ing;
        while ((ing = session.popIngredient()) != null) {
            world.dropItem(centre.clone().add(0, 0.6, 0), ing);
        }
    }

    private void dropSludgeFor(CauldronSession session, World world, Location centre) {
        ItemStack tmpl = sludgeTemplate();
        if (tmpl == null) {
            // Even with AIR sludge configured, we still want to clear out the
            // queue so the session can collapse cleanly.
            while (session.popIngredient() != null) {}
            return;
        }
        ItemStack ing;
        while ((ing = session.popIngredient()) != null) {
            ItemStack drop = tmpl.clone();
            drop.setAmount(Math.max(1, ing.getAmount()));
            world.dropItem(centre.clone().add(0, 0.6, 0), drop);
        }
    }

    private ItemStack nearlyDoneBottle() {
        ConfigManager cfg = plugin.configManager();
        Material mat = cfg.breakLateBottleMaterial();
        if (mat == null || mat == Material.AIR) return null;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = cfg.breakLateBottleName();
            if (name != null && !name.isBlank()) {
                meta.displayName(legacy.deserialize(name));
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private ItemStack sludgeTemplate() {
        ConfigManager cfg = plugin.configManager();
        Material mat = cfg.sludgeMaterial();
        if (mat == null || mat == Material.AIR) return null;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && cfg.sludgeDisplayName() != null && !cfg.sludgeDisplayName().isBlank()) {
            meta.displayName(legacy.deserialize(cfg.sludgeDisplayName()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public Component itemLabel(ItemStack stack) {
        if (stack == null) return Component.empty();
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            Component c = meta.displayName();
            if (c != null) return c;
        }
        return Component.text(stack.getType().name().toLowerCase().replace('_', ' '));
    }
}
