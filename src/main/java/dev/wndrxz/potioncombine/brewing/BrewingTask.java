package dev.wndrxz.potioncombine.brewing;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.api.events.BrewSuccessEvent;
import dev.wndrxz.potioncombine.api.events.PollutionChangeEvent;
import dev.wndrxz.potioncombine.cauldron.CauldronSession;
import dev.wndrxz.potioncombine.config.ConfigManager;
import dev.wndrxz.potioncombine.locale.LocaleManager;
import dev.wndrxz.potioncombine.recipe.Recipe;
import dev.wndrxz.potioncombine.util.Particles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public final class BrewingTask extends BukkitRunnable {

    enum Phase { BREWING, READY }

    private final PotionCombine plugin;
    private final CauldronSession session;
    private final Recipe recipe;
    private final World world;
    private final Location centre;
    private final int totalBrewTicks;
    private final int spoilTicks;
    private final Component recipeName;

    private int ticks;
    private Phase phase = Phase.BREWING;
    private int readyElapsed;

    public BrewingTask(PotionCombine plugin, CauldronSession session, Recipe recipe,
                       World world, Location centre, int totalBrewTicks, int spoilTicks) {
        this(plugin, session, recipe, world, centre, totalBrewTicks, spoilTicks, 0, Phase.BREWING, 0);
    }

    // resume constructor, only used by the state-store restore path on boot;
    // a fresh brew always starts at zero
    public BrewingTask(PotionCombine plugin, CauldronSession session, Recipe recipe,
                       World world, Location centre, int totalBrewTicks, int spoilTicks,
                       int startTicks, Phase startPhase, int readyElapsedStart) {
        this.plugin = plugin;
        this.session = session;
        this.recipe = recipe;
        this.world = world;
        this.centre = centre;
        this.totalBrewTicks = Math.max(1, totalBrewTicks);
        this.spoilTicks = Math.max(0, spoilTicks);
        this.recipeName = LegacyComponentSerializer.legacyAmpersand().deserialize(recipe.displayNameLegacy());
        this.ticks = Math.max(0, Math.min(startTicks, this.totalBrewTicks));
        this.phase = startPhase;
        this.readyElapsed = Math.max(0, readyElapsedStart);
        session.brewTotalTicks(this.totalBrewTicks);
    }

    @Override
    public void run() {
        if (phase == Phase.BREWING) {
            tickBrewing();
        } else {
            tickReady();
        }
    }

    private void tickBrewing() {
        ticks++;
        session.progressFraction(Math.min(1.0, ticks / (double) totalBrewTicks));

        if (world.getBlockAt(centre.getBlockX(), centre.getBlockY(), centre.getBlockZ()).getType()
                != Material.WATER_CAULDRON) {
            plugin.brewingService().onWaterLost(session);
            cancel();
            return;
        }

        if (ticks % 5 == 0) plugin.configManager().particles().play(
                "brewing_bubble", world, centre.clone().add(0, 0.4, 0));
        if (ticks % plugin.configManager().bubbleIntervalTicks() == 0) plugin.soundManager().bubble(centre);
        if (ticks % 4 == 0) updateProgressText();

        if (ticks >= totalBrewTicks) finishBrew();
    }

    private void tickReady() {
        readyElapsed++;
        session.readyElapsedTicks(readyElapsed);
        plugin.displayManager().rotateItem(session.itemDisplayId(), world,
                readyElapsed * plugin.configManager().resultSpinPerTick());

        if (spoilTicks > 0 && readyElapsed >= spoilTicks) spoilNow();
    }

    private void updateProgressText() {
        ConfigManager cfg = plugin.configManager();
        int width = cfg.progressBarWidth();
        int filled = Math.max(0, Math.min(width,
                (int) Math.round(width * (ticks / (double) totalBrewTicks))));
        int percent = (int) Math.round(100.0 * ticks / totalBrewTicks);

        StringBuilder filledBuf = new StringBuilder(filled * cfg.progressFilledChar().length());
        for (int i = 0; i < filled; i++) filledBuf.append(cfg.progressFilledChar());
        StringBuilder emptyBuf = new StringBuilder((width - filled) * cfg.progressEmptyChar().length());
        for (int i = filled; i < width; i++) emptyBuf.append(cfg.progressEmptyChar());

        String rendered = cfg.progressTemplate()
                .replace("<filled>", filledBuf.toString())
                .replace("<empty>",  emptyBuf.toString())
                .replace("<percent>", Integer.toString(percent));

        Component progress = MiniMessage.miniMessage().deserialize(rendered);
        Component out;
        if (cfg.progressShowHeader()) {
            Component header = plugin.locale().get("brew.started",
                    LocaleManager.component("recipe", recipeName));
            out = header.append(Component.newline()).append(progress);
        } else {
            out = progress;
        }
        plugin.displayManager().updateText(session.textDisplayId(), world, out);
    }

    private void finishBrew() {
        ItemStack result = plugin.recipeManager().produce(recipe);
        if (result == null) result = new ItemStack(Material.POTION);

        Block cauldronBlock = world.getBlockAt(centre.getBlockX(), centre.getBlockY(), centre.getBlockZ());
        BrewSuccessEvent ev = new BrewSuccessEvent(cauldronBlock, recipe, result);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.result() != null) result = ev.result();

        session.readyItem(result);
        session.progressFraction(1.0);

        plugin.displayManager().remove(session.textDisplayId(), world);
        session.textDisplayId(null);

        plugin.soundManager().success(centre);
        Color flash = recipe.color() != null ? recipe.color() : Color.fromRGB(255, 220, 64);
        Location dispLoc = centre.clone().add(0, plugin.configManager().displayYOffset(), 0);
        plugin.configManager().particles().play("brew_success", world, dispLoc);
        Particles.spawnDust(world, dispLoc, 18, flash, 1.2f);

        plugin.pollutionManager().add(session.location(),
                plugin.configManager().pollutionPerSuccess(),
                PollutionChangeEvent.Cause.BREW_SUCCESS);

        if (plugin.hopperExtractor().tryExtract(world,
                centre.getBlockX(), centre.getBlockY(), centre.getBlockZ(), result)) {
            plugin.cauldronManager().cancelAllTasks(session);
            plugin.cauldronManager().remove(session.location());
            cancel();
            return;
        }

        // no hopper took it; maybe a neighbouring cauldron is waiting on this result
        if (plugin.synergyManager().feed(world,
                centre.getBlockX(), centre.getBlockY(), centre.getBlockZ(), recipe, result)) {
            plugin.cauldronManager().cancelAllTasks(session);
            plugin.cauldronManager().remove(session.location());
            cancel();
            return;
        }

        java.util.UUID itemId = plugin.displayManager().spawnItem(dispLoc, result);
        session.itemDisplayId(itemId);

        session.state(CauldronSession.State.READY);
        phase = Phase.READY;
        readyElapsed = 0;
    }

    private void spoilNow() {
        Material spoil = plugin.configManager().spoilMaterial();
        if (spoil == null || spoil == Material.AIR) {
            plugin.displayManager().remove(session.itemDisplayId(), world);
            session.itemDisplayId(null);
            session.readyItem(null);
        } else {
            ItemStack spoiled = new ItemStack(spoil);
            session.readyItem(spoiled);
            plugin.displayManager().swapItem(session.itemDisplayId(), world, spoiled);
        }
        plugin.soundManager().spoil(centre);
        session.state(CauldronSession.State.SPOILED);
        plugin.pollutionManager().add(session.location(),
                plugin.configManager().pollutionPerSpoil(),
                PollutionChangeEvent.Cause.BREW_SPOIL);
        // Spoiling is silent for the brewer too — they need to come back and
        // see the smoke. But the area-broadcast helps a passer-by notice.
        plugin.locale().broadcastNearby(centre,
                plugin.configManager().notifyRadius(), "brew.spoiled");
        cancel();
    }
}
