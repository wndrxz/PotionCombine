package dev.wndrxz.potioncombine.recipe;

import dev.wndrxz.potioncombine.util.Keys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multiset matching: every recipe is reduced to a Map&lt;IngredientKey, Integer&gt;
 * once at load time, and the cauldron contents are reduced to the same shape
 * each time the matcher is called. Exact equality wins; if no recipe matches
 * exactly but at least one recipe is a strict superset of the current
 * contents the result is PARTIAL (player can still grow into it). Otherwise
 * NO_MATCH (dead end — the brew should fail immediately).
 */
public final class ItemMatcher {

    public enum Result { MATCH, PARTIAL, NO_MATCH }

    public static final class MatchResult {
        public final Result result;
        public final Recipe matched;

        public MatchResult(Result result, Recipe matched) {
            this.result = result;
            this.matched = matched;
        }
    }

    public MatchResult match(List<ItemStack> contents, Collection<Recipe> recipes) {
        return matchKeys(countItems(contents), recipes);
    }

    public MatchResult matchKeys(Map<IngredientKey, Integer> counts, Collection<Recipe> recipes) {
        if (counts.isEmpty()) {
            // Empty cauldron — could still grow into anything.
            return new MatchResult(recipes.isEmpty() ? Result.NO_MATCH : Result.PARTIAL, null);
        }
        int totalCounts = totalOf(counts);
        Recipe full = null;
        boolean anyPartial = false;

        for (Recipe r : recipes) {
            int cmp = compare(counts, totalCounts, r);
            if (cmp == 0) { full = r; break; }
            if (cmp < 0) anyPartial = true;
        }
        if (full != null) return new MatchResult(Result.MATCH, full);
        return new MatchResult(anyPartial ? Result.PARTIAL : Result.NO_MATCH, null);
    }

    /** -1 = counts is a strict subset of recipe; 0 = exact equality; 1 = counts not a subset. */
    private int compare(Map<IngredientKey, Integer> counts, int totalCounts, Recipe r) {
        Map<IngredientKey, Integer> req = r.required();
        for (Map.Entry<IngredientKey, Integer> e : counts.entrySet()) {
            Integer reqCount = req.get(e.getKey());
            if (reqCount == null || reqCount < e.getValue()) return 1;
        }
        return totalCounts == r.totalRequired() ? 0 : -1;
    }

    public Map<IngredientKey, Integer> countItems(List<ItemStack> items) {
        Map<IngredientKey, Integer> map = new HashMap<>();
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            IngredientKey k = keyOf(it);
            if (k == null) continue;
            map.merge(k, it.getAmount(), Integer::sum);
        }
        return map;
    }

    public IngredientKey keyOf(ItemStack stack) {
        if (stack == null) return null;
        Material mat = stack.getType();
        if (mat == Material.AIR) return null;

        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta != null && Keys.RECIPE_ID != null) {
            String nested = meta.getPersistentDataContainer().get(Keys.RECIPE_ID, PersistentDataType.STRING);
            if (nested != null && !nested.isBlank()) return IngredientKey.nested(nested);
        }

        if (meta instanceof PotionMeta pm) {
            PotionType type = readBasePotionType(pm);
            if (type != null) return IngredientKey.potion(mat, type);
        }
        return IngredientKey.item(mat);
    }

    @SuppressWarnings("deprecation") // 1.20.4 API; 1.20.5+ adds getBasePotionType()
    private static PotionType readBasePotionType(PotionMeta pm) {
        try {
            return pm.getBasePotionData() != null ? pm.getBasePotionData().getType() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int totalOf(Map<IngredientKey, Integer> m) {
        int s = 0;
        for (int v : m.values()) s += v;
        return s;
    }
}
