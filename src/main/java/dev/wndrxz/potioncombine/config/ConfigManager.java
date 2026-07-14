package dev.wndrxz.potioncombine.config;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.display.ParticleConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.logging.Level;

public final class ConfigManager {

    private final PotionCombine plugin;

    private FileConfiguration root;

    private String localeRaw = "auto";
    private int    maxRecipes = 20;
    private int    gracePeriodTicks = 60;
    private boolean ingredientRetrieval = true;
    private int    cooldownSeconds = 0;
    private int    notifyRadius = 16;

    // Reserved for a paid edition. False today, never gates anything yet —
    // the flag exists so the schema stays stable when premium ships.
    private boolean premium = false;

    private int    defaultBrewTicks = 200;
    private int    overbrewSeconds = 60;
    private Material spoilMaterial = Material.ROTTEN_FLESH;
    private Material sludgeMaterial = Material.ROTTEN_FLESH;
    private String sludgeDisplayName = "&8&oAlchemical Sludge";
    private int    bubbleIntervalTicks = 14;

    // Cauldron-break stage thresholds (fractions of brew progress).
    private double breakEarlyUntil = 0.25;
    private double breakLateFrom   = 0.75;
    private Material breakLateBottleMaterial = Material.POTION;
    private String   breakLateBottleName = "&7&oUnfinished brew";

    private String soundInsert    = "minecraft:entity.item.pickup";
    private String soundReject    = "minecraft:entity.villager.no";
    private String soundRetrieve  = "minecraft:entity.item.pickup";
    private String soundBubble    = "minecraft:block.brewing_stand.brew";
    private String soundSuccess   = "minecraft:entity.player.levelup";
    private String soundFail      = "minecraft:block.glass.break";
    private String soundSpoil     = "minecraft:block.fire.extinguish";
    private String soundBrush     = "minecraft:item.brush.brushing.generic";
    private String soundIdle      = "";
    private String soundBreak     = "minecraft:block.cauldron.empty";

    private double displayYOffset = 1.05;
    private double resultSpinPerTick = 0.06;
    private int    progressBarWidth = 16;
    private boolean progressShowHeader = true;
    private String  progressFilledChar = "|";
    private String  progressEmptyChar  = "|";
    private String  progressTemplate   =
            "<dark_gray>[<gradient:#7a4cff:#22d3ee><filled></gradient><dark_gray><empty>] <gray><percent>%";

    private final ParticleConfig particles = new ParticleConfig();
    private final WorldFilter worldFilter = new WorldFilter();

    private boolean pollutionEnabled = true;
    private int    pollutionPerSuccess = 1;
    private int    pollutionPerSpoil   = 3;
    private int    pollutionPerFail    = 2;
    private int    pollutionThreshold  = 20;
    private double pollutionSpoilChancePerPoint = 0.01;
    private double pollutionBrewTimeFactorPerPoint = 0.0;

    private boolean cleaningEnabled = true;
    private int     cleaningPerBrushClick = 2;
    private boolean cleaningResetOnWaterRefill = true;
    private boolean cleaningConsumesBrushDurability = true;

    private boolean hopperExtractEnabled = false;

    private boolean synergyEnabled = true;
    private int     synergyMaxHoldSeconds = 30;

    private boolean progressionEnabled = true;
    private boolean journalEnabled = true;
    private boolean restoreLiveBrews = true;

    public ConfigManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.root = plugin.getConfig();

        localeRaw          = root.getString("settings.locale", "auto");
        maxRecipes         = root.getInt("settings.max_recipes", 20);
        gracePeriodTicks   = Math.max(1, root.getInt("settings.grace_period_ticks", 60));
        ingredientRetrieval = root.getBoolean("settings.ingredient_retrieval", true);
        cooldownSeconds    = Math.max(0, root.getInt("settings.cooldown_seconds", 0));
        notifyRadius       = Math.max(0, root.getInt("settings.notify_radius_blocks", 16));
        premium            = root.getBoolean("settings.premium", false);

        defaultBrewTicks   = Math.max(20, root.getInt("brewing.default_brew_time_ticks", 200));
        overbrewSeconds    = Math.max(0, root.getInt("brewing.overbrew_time_seconds", 60));
        spoilMaterial      = parseMaterial(root.getString("brewing.spoil_material", "ROTTEN_FLESH"), Material.ROTTEN_FLESH);
        sludgeMaterial     = parseMaterial(root.getString("brewing.sludge_material", "ROTTEN_FLESH"), Material.ROTTEN_FLESH);
        sludgeDisplayName  = root.getString("brewing.sludge_display_name", "&8&oAlchemical Sludge");
        bubbleIntervalTicks = Math.max(1, root.getInt("brewing.bubble_sound_interval_ticks", 14));

        breakEarlyUntil    = clampFraction(root.getDouble("brewing.break_stages.early_until", 0.25));
        breakLateFrom      = clampFraction(root.getDouble("brewing.break_stages.late_from",   0.75));
        if (breakEarlyUntil > breakLateFrom) breakEarlyUntil = breakLateFrom;
        breakLateBottleMaterial = parseMaterial(
                root.getString("brewing.break_stages.late_bottle_material", "POTION"), Material.POTION);
        breakLateBottleName = root.getString("brewing.break_stages.late_bottle_name", "&7&oUnfinished brew");

        soundInsert   = root.getString("sounds.ingredient_insert",  soundInsert);
        soundReject   = root.getString("sounds.ingredient_reject",  soundReject);
        soundRetrieve = root.getString("sounds.ingredient_retrieve", soundRetrieve);
        soundBubble   = root.getString("sounds.brew_bubble",        soundBubble);
        soundSuccess  = root.getString("sounds.brew_success",       soundSuccess);
        soundFail     = root.getString("sounds.brew_fail",          soundFail);
        soundSpoil    = root.getString("sounds.spoil",              soundSpoil);
        soundBrush    = root.getString("sounds.cleaning_brush",     soundBrush);
        soundIdle     = root.getString("sounds.pollution_idle",     soundIdle);
        soundBreak    = root.getString("sounds.cauldron_broken",    soundBreak);

        displayYOffset      = root.getDouble("display.y_offset", 1.05);
        resultSpinPerTick   = root.getDouble("display.result_spin_per_tick", 0.06);
        progressBarWidth    = Math.max(4, root.getInt("display.progress.bar_width", root.getInt("display.progress_bar_width", 16)));
        progressShowHeader  = root.getBoolean("display.progress.show_header", true);
        progressFilledChar  = nonEmpty(root.getString("display.progress.filled_char", "|"), "|");
        progressEmptyChar   = nonEmpty(root.getString("display.progress.empty_char",  "|"), "|");
        progressTemplate    = root.getString("display.progress.template", progressTemplate);

        particles.load(root.getConfigurationSection("display.particles"), plugin.getLogger());
        worldFilter.load(root.getConfigurationSection("settings.worlds"));

        pollutionEnabled               = root.getBoolean("pollution.enabled", true);
        pollutionPerSuccess            = Math.max(0, root.getInt("pollution.per_success", 1));
        pollutionPerSpoil              = Math.max(0, root.getInt("pollution.per_spoil",   3));
        pollutionPerFail               = Math.max(0, root.getInt("pollution.per_fail",    2));
        pollutionThreshold             = Math.max(1, root.getInt("pollution.threshold",  20));
        pollutionSpoilChancePerPoint   = clamp01(root.getDouble("pollution.spoil_chance_per_point", 0.01));
        pollutionBrewTimeFactorPerPoint = Math.max(0.0, root.getDouble("pollution.brew_time_factor_per_point", 0.0));

        cleaningEnabled                = root.getBoolean("cleaning.enabled", true);
        cleaningPerBrushClick          = Math.max(1, root.getInt("cleaning.per_brush_click", 2));
        cleaningResetOnWaterRefill     = root.getBoolean("cleaning.reset_on_water_refill", true);
        cleaningConsumesBrushDurability = root.getBoolean("cleaning.consume_brush_durability", true);

        hopperExtractEnabled = root.getBoolean("automation.hopper_extract", false);

        synergyEnabled        = root.getBoolean("synergy.enabled", true);
        synergyMaxHoldSeconds = Math.max(0, root.getInt("synergy.max_hold_seconds", 30));

        progressionEnabled = root.getBoolean("progression.enabled", true);
        journalEnabled     = root.getBoolean("progression.journal_enabled", true);
        restoreLiveBrews   = root.getBoolean("progression.restore_live_brews", true);

        if (plugin.heatSourceManager() != null) {
            plugin.heatSourceManager().load(root.getConfigurationSection("heat"));
        }
    }

    private static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

    private static double clampFraction(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static String nonEmpty(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    public YamlConfiguration loadRecipes() {
        File f = new File(plugin.getDataFolder(), "recipes.yml");
        if (!f.exists()) {
            plugin.saveResource("recipes.yml", false);
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    public Locale resolveLocale(String[] shipped) {
        String pick;
        if (localeRaw == null || localeRaw.isBlank() || localeRaw.equalsIgnoreCase("auto")) {
            String sys = Locale.getDefault().getLanguage();
            pick = contains(shipped, sys) ? sys : "en";
        } else {
            pick = contains(shipped, localeRaw.toLowerCase(Locale.ROOT)) ? localeRaw.toLowerCase(Locale.ROOT) : "en";
        }
        return Locale.forLanguageTag(pick);
    }

    private static boolean contains(String[] arr, String s) {
        for (String x : arr) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    private Material parseMaterial(String raw, Material def) {
        if (raw == null) return def;
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING, "Unknown material '" + raw + "', falling back to " + def.name());
            return def;
        }
    }

    public int maxRecipes() { return maxRecipes; }
    public int gracePeriodTicks() { return gracePeriodTicks; }
    public boolean ingredientRetrieval() { return ingredientRetrieval; }
    public int cooldownSeconds() { return cooldownSeconds; }
    public int notifyRadius() { return notifyRadius; }
    public boolean premium() { return premium; }

    public int defaultBrewTicks() { return defaultBrewTicks; }
    public int overbrewSeconds() { return overbrewSeconds; }
    public Material spoilMaterial() { return spoilMaterial; }
    public Material sludgeMaterial() { return sludgeMaterial; }
    public String sludgeDisplayName() { return sludgeDisplayName; }
    public int bubbleIntervalTicks() { return bubbleIntervalTicks; }

    public double breakEarlyUntil() { return breakEarlyUntil; }
    public double breakLateFrom()   { return breakLateFrom; }
    public Material breakLateBottleMaterial() { return breakLateBottleMaterial; }
    public String breakLateBottleName() { return breakLateBottleName; }

    public String soundInsert()   { return soundInsert; }
    public String soundReject()   { return soundReject; }
    public String soundRetrieve() { return soundRetrieve; }
    public String soundBubble()   { return soundBubble; }
    public String soundSuccess()  { return soundSuccess; }
    public String soundFail()     { return soundFail; }
    public String soundSpoil()    { return soundSpoil; }
    public String soundBrush()    { return soundBrush; }
    public String soundIdle()     { return soundIdle; }
    public String soundBreak()    { return soundBreak; }

    public double displayYOffset() { return displayYOffset; }
    public double resultSpinPerTick() { return resultSpinPerTick; }
    public int progressBarWidth() { return progressBarWidth; }
    public boolean progressShowHeader() { return progressShowHeader; }
    public String progressFilledChar() { return progressFilledChar; }
    public String progressEmptyChar()  { return progressEmptyChar; }
    public String progressTemplate()   { return progressTemplate; }

    public ParticleConfig particles() { return particles; }
    public WorldFilter worldFilter() { return worldFilter; }

    public boolean pollutionEnabled() { return pollutionEnabled; }
    public int pollutionPerSuccess() { return pollutionPerSuccess; }
    public int pollutionPerSpoil()   { return pollutionPerSpoil; }
    public int pollutionPerFail()    { return pollutionPerFail; }
    public int pollutionThreshold()  { return pollutionThreshold; }
    public double pollutionSpoilChancePerPoint() { return pollutionSpoilChancePerPoint; }
    public double pollutionBrewTimeFactorPerPoint() { return pollutionBrewTimeFactorPerPoint; }

    public boolean cleaningEnabled() { return cleaningEnabled; }
    public int     cleaningPerBrushClick() { return cleaningPerBrushClick; }
    public boolean cleaningResetOnWaterRefill() { return cleaningResetOnWaterRefill; }
    public boolean cleaningConsumesBrushDurability() { return cleaningConsumesBrushDurability; }

    public boolean hopperExtractEnabled() { return hopperExtractEnabled; }

    public boolean synergyEnabled() { return synergyEnabled; }
    public int synergyMaxHoldSeconds() { return synergyMaxHoldSeconds; }

    public boolean progressionEnabled() { return progressionEnabled; }
    public boolean journalEnabled() { return journalEnabled; }
    public boolean restoreLiveBrews() { return restoreLiveBrews; }
}
