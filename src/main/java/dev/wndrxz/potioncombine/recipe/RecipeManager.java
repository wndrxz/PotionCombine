package dev.wndrxz.potioncombine.recipe;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class RecipeManager {

    private final PotionCombine plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private final Map<String, Recipe> byId = new LinkedHashMap<>();
    private final Map<String, ItemStack> resultCache = new LinkedHashMap<>();

    public RecipeManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public void load(YamlConfiguration cfg, int maxRecipes, int defaultBrewTicks) {
        byId.clear();
        resultCache.clear();

        List<String> ids = new ArrayList<>(cfg.getKeys(false));
        int loaded = 0, skipped = 0;
        for (String id : ids) {
            if (loaded >= maxRecipes) {
                plugin.locale().sendPlain(plugin.getServer().getConsoleSender(),
                        "startup.recipes_capped",
                        dev.wndrxz.potioncombine.locale.LocaleManager.placeholder("max", String.valueOf(maxRecipes)));
                break;
            }
            ConfigurationSection sec = cfg.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                Recipe r = parse(id, sec, defaultBrewTicks);
                byId.put(id, r);
                resultCache.put(id, buildResult(r));
                loaded++;
            } catch (Exception ex) {
                skipped++;
                plugin.getLogger().log(Level.WARNING,
                        "Skipping recipe '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + loaded + " recipe(s), skipped " + skipped + ".");
    }

    public Collection<Recipe> all() { return Collections.unmodifiableCollection(byId.values()); }
    public Recipe get(String id) { return byId.get(id); }
    public boolean contains(String id) { return byId.containsKey(id); }

    /** Returns true if this item belongs to any recipe (as a raw ingredient OR
     *  as a nested custom potion). Used by the cauldron listener to reject
     *  un-whitelisted items at insert time. */
    public boolean isWhitelisted(IngredientKey key) {
        if (key == null) return false;
        for (Recipe r : byId.values()) {
            if (r.required().containsKey(key)) return true;
        }
        return false;
    }

    public ItemStack produce(Recipe recipe) {
        ItemStack template = resultCache.get(recipe.id());
        return template == null ? null : template.clone();
    }

    public ItemStack produce(String recipeId) {
        Recipe r = byId.get(recipeId);
        return r == null ? null : produce(r);
    }

    private Recipe parse(String id, ConfigurationSection sec, int defaultBrewTicks) {
        String displayName = sec.getString("display_name", id);
        List<String> lore = sec.getStringList("lore");
        int brewTicks = sec.getInt("brew_time_ticks", defaultBrewTicks);
        Color color = parseColor(sec.getString("color"));
        PotionType base = parsePotionType(sec.getString("base_potion"), PotionType.AWKWARD);
        List<Ingredient> ings = parseIngredients(sec.getList("ingredients"));
        if (ings.isEmpty()) throw new IllegalArgumentException("no ingredients");
        List<PotionEffect> effects = parseEffects(sec.getMapList("effects"));
        return new Recipe(id, displayName, lore, brewTicks, color, base, ings, effects);
    }

    @SuppressWarnings("unchecked")
    private List<Ingredient> parseIngredients(List<?> raw) {
        if (raw == null) return List.of();
        List<Ingredient> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException("ingredient must be a map");
            }
            int amount = 1;
            Object amt = m.get("amount");
            if (amt instanceof Number n) amount = Math.max(1, n.intValue());

            Object nested = m.get("recipe");
            if (nested instanceof String s && !s.isBlank()) {
                out.add(new Ingredient(IngredientKey.nested(s.trim()), amount));
                continue;
            }

            Object matRaw = m.get("material");
            if (!(matRaw instanceof String matStr)) {
                throw new IllegalArgumentException("ingredient needs 'material' or 'recipe'");
            }
            Material mat;
            try {
                mat = Material.valueOf(matStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unknown material '" + matStr + "'");
            }

            Object ptRaw = m.get("potion_type");
            if (ptRaw instanceof String ptStr && !ptStr.isBlank()) {
                PotionType type = parsePotionType(ptStr, null);
                if (type == null) throw new IllegalArgumentException("unknown potion_type '" + ptStr + "'");
                out.add(new Ingredient(IngredientKey.potion(mat, type), amount));
            } else {
                out.add(new Ingredient(IngredientKey.item(mat), amount));
            }
        }
        return out;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<PotionEffect> parseEffects(List<Map<?, ?>> rawList) {
        if (rawList == null || rawList.isEmpty()) return List.of();
        List<PotionEffect> out = new ArrayList<>(rawList.size());
        for (Map raw : rawList) {
            Object t = raw.get("type");
            if (!(t instanceof String typeStr)) throw new IllegalArgumentException("effect missing type");
            PotionEffectType type = PotionEffectType.getByName(typeStr.trim().toUpperCase(Locale.ROOT));
            if (type == null) throw new IllegalArgumentException("unknown effect type '" + typeStr + "'");
            int dur = raw.get("duration") instanceof Number n ? n.intValue() : 600;
            int amp = raw.get("amplifier") instanceof Number n ? n.intValue() : 0;
            boolean ambient = raw.get("ambient") instanceof Boolean b && b;
            boolean particles = !(raw.get("particles") instanceof Boolean b) || b;
            boolean icon = !(raw.get("icon") instanceof Boolean b) || b;
            out.add(new PotionEffect(type, dur, amp, ambient, particles, icon));
        }
        return out;
    }

    private Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            int rgb = Integer.parseInt(s, 16);
            return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private PotionType parsePotionType(String raw, PotionType def) {
        if (raw == null || raw.isBlank()) return def;
        String normalized = normalizePotionType(raw);
        try {
            return PotionType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private static String normalizePotionType(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "HEALING" -> "INSTANT_HEAL";
            case "HARMING" -> "INSTANT_DAMAGE";
            case "REGENERATION" -> "REGEN";
            case "LEAPING" -> "JUMP";
            case "SWIFTNESS" -> "SPEED";
            default -> s;
        };
    }

    // TODO: move to setBasePotionType() once we drop 1.20.4 support
    @SuppressWarnings("deprecation") // PotionData is 1.20.4 surface
    private ItemStack buildResult(Recipe recipe) {
        ItemStack stack = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        if (meta == null) return stack;

        try {
            PotionType base = recipe.basePotion() == null ? PotionType.AWKWARD : recipe.basePotion();
            meta.setBasePotionData(new PotionData(base, false, false));
        } catch (Throwable ignored) {
            // Some 1.21 builds restrict PotionData — safe to ignore, custom effects still apply.
        }

        Component name = legacy.deserialize(recipe.displayNameLegacy())
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        if (!recipe.loreLegacy().isEmpty()) {
            List<Component> lore = new ArrayList<>(recipe.loreLegacy().size());
            for (String line : recipe.loreLegacy()) {
                lore.add(legacy.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        if (recipe.color() != null) meta.setColor(recipe.color());

        for (PotionEffect eff : recipe.effects()) {
            meta.addCustomEffect(eff, true);
        }

        NamespacedKey idKey = Keys.RECIPE_ID;
        NamespacedKey verKey = Keys.RECIPE_VERSION;
        if (idKey != null)  meta.getPersistentDataContainer().set(idKey,  PersistentDataType.STRING,  recipe.id());
        if (verKey != null) meta.getPersistentDataContainer().set(verKey, PersistentDataType.INTEGER, 1);

        stack.setItemMeta(meta);
        return stack;
    }
}
