package dev.wndrxz.potioncombine;

import dev.wndrxz.potioncombine.api.PotionCombineAPI;
import dev.wndrxz.potioncombine.brewing.BrewingService;
import dev.wndrxz.potioncombine.brewing.HopperExtractor;
import dev.wndrxz.potioncombine.cauldron.CauldronManager;
import dev.wndrxz.potioncombine.cauldron.CauldronSession;
import dev.wndrxz.potioncombine.command.PotionCombineCommand;
import dev.wndrxz.potioncombine.config.ConfigManager;
import dev.wndrxz.potioncombine.cooldown.CooldownManager;
import dev.wndrxz.potioncombine.display.DisplayManager;
import dev.wndrxz.potioncombine.heat.HeatSourceManager;
import dev.wndrxz.potioncombine.integration.PlaceholderHook;
import dev.wndrxz.potioncombine.locale.LocaleManager;
import dev.wndrxz.potioncombine.persistence.StateStore;
import dev.wndrxz.potioncombine.recipe.ItemMatcher;
import dev.wndrxz.potioncombine.recipe.RecipeManager;
import dev.wndrxz.potioncombine.listener.CauldronListener;
import dev.wndrxz.potioncombine.pollution.PollutionManager;
import dev.wndrxz.potioncombine.sound.SoundManager;
import dev.wndrxz.potioncombine.util.Keys;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class PotionCombine extends JavaPlugin {

    private ConfigManager configManager;
    private LocaleManager localeManager;
    private RecipeManager recipeManager;
    private CauldronManager cauldronManager;
    private DisplayManager displayManager;
    private SoundManager soundManager;
    private BrewingService brewingService;
    private ItemMatcher itemMatcher;
    private PollutionManager pollutionManager;
    private HeatSourceManager heatSourceManager;
    private CooldownManager cooldownManager;
    private HopperExtractor hopperExtractor;
    private PlaceholderHook placeholderHook;
    private StateStore stateStore;

    @Override
    public void onEnable() {
        Keys.init(this);

        // HeatSourceManager has to exist before ConfigManager.load() so the
        // first config pass can populate its source map.
        this.heatSourceManager = new HeatSourceManager(this);

        this.configManager = new ConfigManager(this);
        configManager.load();

        this.localeManager = new LocaleManager(this);
        Locale resolved = configManager.resolveLocale(LocaleManager.shippedLanguages());
        localeManager.load(resolved);
        getLogger().info("Locale resolved to '" + resolved.getLanguage() + "'.");

        this.itemMatcher = new ItemMatcher();
        this.recipeManager = new RecipeManager(this);
        YamlConfiguration recipes = configManager.loadRecipes();
        recipeManager.load(recipes, configManager.maxRecipes(), configManager.defaultBrewTicks());

        this.cauldronManager = new CauldronManager(this);
        this.displayManager = new DisplayManager(this);
        this.soundManager = new SoundManager(this);
        this.pollutionManager = new PollutionManager(this);
        this.cooldownManager = new CooldownManager(this);
        this.hopperExtractor = new HopperExtractor(this);
        this.brewingService = new BrewingService(this);
        this.placeholderHook = new PlaceholderHook(this);
        this.stateStore = new StateStore(this);

        getServer().getPluginManager().registerEvents(new CauldronListener(this), this);

        PluginCommand cmd = getCommand("potioncombine");
        if (cmd != null) {
            PotionCombineCommand handler = new PotionCombineCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        } else {
            getLogger().warning("Command 'potioncombine' missing from plugin.yml — check the build.");
        }

        int sweep = displayManager.sweepOrphans();
        if (sweep > 0) getLogger().info("Removed " + sweep + " orphan display entit(y/ies).");

        // Restore pollution + drop persisted ingredients back next to their
        // cauldrons so a hard crash mid-brew is no longer a black hole.
        stateStore.load();

        // Bind the public API and register the optional PAPI expansion. The
        // facade is deliberately the very last thing wired up so it can rely
        // on every manager being present.
        PotionCombineAPI.bind(this);
        placeholderHook.tryRegister();
    }

    @Override
    public void onDisable() {
        if (cauldronManager == null) return;

        // Snapshot persistent state BEFORE we cancel tasks and drop ingredients
        // — those steps wipe the in-memory model.
        if (stateStore != null) stateStore.save();

        World fallback = getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().get(0);
        for (CauldronSession s : cauldronManager.all().values()) {
            cauldronManager.cancelAllTasks(s);
            World w = getServer().getWorld(s.location().worldId());
            if (w != null) {
                displayManager.remove(s.textDisplayId(), w);
                displayManager.remove(s.itemDisplayId(), w);
            }
        }
        cauldronManager.dropAllIngredients(fallback);
        cauldronManager.all().clear();
        if (pollutionManager != null) pollutionManager.shutdown();
        if (cooldownManager  != null) cooldownManager.clear();
    }

    /** Reload both config.yml and recipes.yml on the fly. Sessions in
     *  progress are intentionally left alone — interrupting a live brew on
     *  reload would be a worse experience than letting it finish. */
    public void reloadEverything() {
        configManager.load();
        Locale resolved = configManager.resolveLocale(LocaleManager.shippedLanguages());
        localeManager.load(resolved);

        YamlConfiguration recipes = configManager.loadRecipes();
        recipeManager.load(recipes, configManager.maxRecipes(), configManager.defaultBrewTicks());
    }

    public ConfigManager configManager()   { return configManager; }
    public LocaleManager locale()          { return localeManager; }
    public RecipeManager recipeManager()   { return recipeManager; }
    public CauldronManager cauldronManager(){ return cauldronManager; }
    public DisplayManager displayManager() { return displayManager; }
    public SoundManager soundManager()     { return soundManager; }
    public BrewingService brewingService() { return brewingService; }
    public ItemMatcher itemMatcher()       { return itemMatcher; }
    public PollutionManager pollutionManager() { return pollutionManager; }
    public HeatSourceManager heatSourceManager() { return heatSourceManager; }
    public CooldownManager cooldownManager() { return cooldownManager; }
    public HopperExtractor hopperExtractor() { return hopperExtractor; }
    public PlaceholderHook placeholderHook() { return placeholderHook; }
}
