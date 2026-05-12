package dev.wndrxz.potioncombine.listener;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.api.events.PollutionChangeEvent;
import dev.wndrxz.potioncombine.brewing.BrewingService;
import dev.wndrxz.potioncombine.cauldron.CauldronSession;
import dev.wndrxz.potioncombine.locale.LocaleManager;
import dev.wndrxz.potioncombine.recipe.IngredientKey;
import dev.wndrxz.potioncombine.util.BlockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public final class CauldronListener implements Listener {

    private final PotionCombine plugin;

    public CauldronListener(PotionCombine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();
        if (type != Material.WATER_CAULDRON && type != Material.CAULDRON) return;
        if (!plugin.configManager().worldFilter().allows(block.getWorld())) return;

        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean sneaking = player.isSneaking();

        // Check for ready/spoiled brew collection FIRST, before any other
        // interactions. This prevents vanilla cauldron behavior (filling
        // bottles, bucket interactions) from interfering with potion pickup.
        if (action == Action.RIGHT_CLICK_BLOCK && !sneaking) {
            CauldronSession s = plugin.cauldronManager().get(block);
            if (s != null && (s.state() == CauldronSession.State.READY
                    || s.state() == CauldronSession.State.SPOILED)) {
                // Cancel immediately to prevent ANY vanilla interaction.
                event.setCancelled(true);
                plugin.brewingService().collectReady(s, player);
                return;
            }
        }

        if (action == Action.RIGHT_CLICK_BLOCK && !sneaking) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand != null && inHand.getType() == Material.BRUSH
                    && plugin.configManager().cleaningEnabled()) {
                if (handleBrush(player, block, inHand)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (action == Action.LEFT_CLICK_BLOCK && sneaking) {
            event.setCancelled(true);
            handleInsert(player, block);
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && sneaking) {
            // Retrieval is the only sneaking right-click we care about.
            if (handleRetrieve(player, block)) {
                event.setCancelled(true);
            }
            return;
        }
    }

    private void handleInsert(Player player, Block block) {
        if (block.getType() != Material.WATER_CAULDRON) {
            plugin.locale().send(player, "cauldron.empty_no_water");
            plugin.soundManager().reject(player, block.getLocation().toCenterLocation());
            return;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) return;

        BlockKey bkey = BlockKey.of(block);
        if (plugin.pollutionManager().isBlocked(bkey)) {
            plugin.locale().send(player, "cauldron.too_polluted");
            plugin.soundManager().reject(player, block.getLocation().toCenterLocation());
            return;
        }

        if (!plugin.cooldownManager().canBrew(player)) {
            int secs = plugin.cooldownManager().remainingSeconds(player.getUniqueId());
            plugin.locale().send(player, "cauldron.cooldown",
                    LocaleManager.placeholder("seconds", Integer.toString(secs)));
            plugin.soundManager().reject(player, block.getLocation().toCenterLocation());
            return;
        }

        // Check whitelist BEFORE touching the session map, otherwise a stray
        // shift+LMB with a non-recipe item would leave an empty session behind.
        ItemStack candidate = inHand.clone();
        candidate.setAmount(1);
        IngredientKey key = plugin.itemMatcher().keyOf(candidate);
        if (key == null || !plugin.recipeManager().isWhitelisted(key)) {
            plugin.locale().send(player, "cauldron.not_whitelisted");
            plugin.soundManager().reject(player, block.getLocation().toCenterLocation());
            return;
        }

        CauldronSession session = plugin.cauldronManager().getOrCreate(block);
        if (session.state() != CauldronSession.State.COLLECTING) {
            // Already brewing / ready — silently ignore further inserts.
            plugin.soundManager().reject(player, block.getLocation().toCenterLocation());
            return;
        }

        // Consume one from hand (creative players keep their stack — match vanilla).
        if (player.getGameMode() != GameMode.CREATIVE) {
            int amt = inHand.getAmount();
            if (amt <= 1) inHand.setAmount(0);
            else inHand.setAmount(amt - 1);
        }

        session.addIngredient(candidate);
        session.lastInsertTick(plugin.getServer().getCurrentTick());

        Location centre = block.getLocation().toCenterLocation();
        plugin.soundManager().insert(player, centre);
        plugin.configManager().particles().play("insert", centre.getWorld(), centre.clone().add(0, 0.4, 0));

        Component label = plugin.brewingService().itemLabel(candidate);
        plugin.locale().send(player, "cauldron.added", LocaleManager.component("item", label));

        plugin.brewingService().onIngredientAdded(session, player);
    }

    private boolean handleRetrieve(Player player, Block block) {
        if (!plugin.configManager().ingredientRetrieval()) return false;
        if (block.getType() != Material.WATER_CAULDRON && block.getType() != Material.CAULDRON) return false;

        CauldronSession session = plugin.cauldronManager().get(block);
        if (session == null || session.isEmpty()) {
            plugin.locale().send(player, "cauldron.retrieve_none");
            return true;
        }
        if (session.state() != CauldronSession.State.COLLECTING) {
            plugin.locale().send(player, "cauldron.retrieve_locked");
            return true;
        }

        ItemStack item = session.popIngredient();
        plugin.cauldronManager().cancelGrace(session);

        var overflow = player.getInventory().addItem(item);
        if (!overflow.isEmpty()) {
            for (ItemStack o : overflow.values()) player.getWorld().dropItem(player.getLocation(), o);
        }

        Location centre = block.getLocation().toCenterLocation();
        plugin.soundManager().retrieve(player, centre);

        Component label = plugin.brewingService().itemLabel(item);
        plugin.locale().send(player, "cauldron.retrieved", LocaleManager.component("item", label));

        if (session.isEmpty()) plugin.cauldronManager().remove(session.location());
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.WATER_CAULDRON && type != Material.CAULDRON) return;
        if (!plugin.configManager().worldFilter().allows(block.getWorld())) return;

        BlockKey key = BlockKey.of(block);
        CauldronSession session = plugin.cauldronManager().get(block);
        if (session != null) {
            plugin.brewingService().onCauldronBroken(session);
        } else if (plugin.pollutionManager().level(key) > 0) {
            // No session to drop, but the pollution still needs to go so a
            // replacement cauldron does not inherit a stranger's grime.
            plugin.pollutionManager().clear(key, PollutionChangeEvent.Cause.EXTERNAL);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        Block block = event.getBlock();
        if (!plugin.configManager().worldFilter().allows(block.getWorld())) return;
        CauldronSession session = plugin.cauldronManager().get(block);

        // Refilling an empty (or non-water) cauldron with water resets the
        // accumulated pollution, if the operator opted into that behaviour.
        // We check this whether or not a session exists.
        boolean fillingWithWater = event.getNewLevel() > 0
                && event.getReason() == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY;
        if (fillingWithWater && plugin.configManager().cleaningResetOnWaterRefill()) {
            BlockKey key = BlockKey.of(block);
            if (plugin.pollutionManager().level(key) > 0) {
                plugin.pollutionManager().clear(key, PollutionChangeEvent.Cause.WATER_REFILL);
            }
        }

        if (session == null) return;

        // The matchers that matter: water emptied or replaced with lava/snow.
        boolean drained = event.getNewLevel() <= 0;
        CauldronLevelChangeEvent.ChangeReason reason = event.getReason();
        boolean relevant = drained
                || reason == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY
                || reason == CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL
                || reason == CauldronLevelChangeEvent.ChangeReason.EVAPORATE;
        if (!relevant) return;

        if (session.state() == CauldronSession.State.BREWING
                || session.state() == CauldronSession.State.COLLECTING) {
            plugin.brewingService().failBrew(session, BrewingService.FailureReason.WATER_LOST, null);
        }
    }

    /** Right-click with a brush on a (water) cauldron. Reduces pollution and
     *  optionally damages the brush. Returns true when the click counts as a
     *  cleaning interaction (so the listener cancels the vanilla event). */
    private boolean handleBrush(Player player, Block block, ItemStack brush) {
        BlockKey key = BlockKey.of(block);
        int before = plugin.pollutionManager().level(key);

        // Nothing to scrub — let the vanilla brush click pass through.
        if (before <= 0) return false;

        Location centre = block.getLocation().toCenterLocation();

        int reduce = plugin.configManager().cleaningPerBrushClick();
        plugin.pollutionManager().reduce(key, reduce, PollutionChangeEvent.Cause.BRUSH);
        int after = plugin.pollutionManager().level(key);

        if (plugin.configManager().cleaningConsumesBrushDurability()
                && player.getGameMode() != GameMode.CREATIVE) {
            if (brush.getItemMeta() instanceof Damageable dmg) {
                int max = brush.getType().getMaxDurability();
                int next = dmg.getDamage() + 1;
                if (max > 0 && next >= max) {
                    brush.setAmount(0);
                } else {
                    dmg.setDamage(next);
                    brush.setItemMeta(dmg);
                }
            }
        }

        plugin.configManager().particles().play("cleaning_brush", centre.getWorld(),
                centre.clone().add(0, 0.4, 0));
        plugin.soundManager().brush(centre);

        if (after <= 0) {
            plugin.locale().send(player, "cleaning.cleared");
        } else {
            plugin.locale().send(player, "cleaning.progress",
                    Placeholder.unparsed("level", Integer.toString(after)),
                    Placeholder.unparsed("threshold", Integer.toString(plugin.pollutionManager().threshold())));
        }
        return true;
    }
}
