package dev.wndrxz.potioncombine.recipe;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemMatcherTest {

    private final ItemMatcher matcher = new ItemMatcher();

    private static Recipe r(String id, List<Ingredient> ings) {
        return new Recipe(id, "&f" + id, List.of(), 200, null, null, ings, List.of());
    }

    @Test
    void exactMatchHits() {
        Recipe heal = r("heal", List.of(
                new Ingredient(IngredientKey.item(Material.REDSTONE), 1),
                new Ingredient(IngredientKey.item(Material.GLOWSTONE_DUST), 1)
        ));
        Map<IngredientKey, Integer> contents = new HashMap<>();
        contents.put(IngredientKey.item(Material.REDSTONE), 1);
        contents.put(IngredientKey.item(Material.GLOWSTONE_DUST), 1);

        ItemMatcher.MatchResult res = matcher.matchKeys(contents, List.of(heal));
        assertEquals(ItemMatcher.Result.MATCH, res.result);
        assertNotNull(res.matched);
        assertEquals("heal", res.matched.id());
    }

    @Test
    void subsetIsPartial() {
        Recipe heal = r("heal", List.of(
                new Ingredient(IngredientKey.item(Material.REDSTONE), 1),
                new Ingredient(IngredientKey.item(Material.GLOWSTONE_DUST), 1)
        ));
        Map<IngredientKey, Integer> contents = new HashMap<>();
        contents.put(IngredientKey.item(Material.REDSTONE), 1);

        ItemMatcher.MatchResult res = matcher.matchKeys(contents, List.of(heal));
        assertEquals(ItemMatcher.Result.PARTIAL, res.result);
        assertNull(res.matched);
    }

    @Test
    void overflowIsDeadEnd() {
        Recipe heal = r("heal", List.of(
                new Ingredient(IngredientKey.item(Material.REDSTONE), 1)
        ));
        Map<IngredientKey, Integer> contents = new HashMap<>();
        contents.put(IngredientKey.item(Material.REDSTONE), 2);

        ItemMatcher.MatchResult res = matcher.matchKeys(contents, List.of(heal));
        assertEquals(ItemMatcher.Result.NO_MATCH, res.result);
    }

    @Test
    void unknownIngredientIsDeadEnd() {
        Recipe heal = r("heal", List.of(
                new Ingredient(IngredientKey.item(Material.REDSTONE), 1)
        ));
        Map<IngredientKey, Integer> contents = new HashMap<>();
        contents.put(IngredientKey.item(Material.SPIDER_EYE), 1);

        ItemMatcher.MatchResult res = matcher.matchKeys(contents, List.of(heal));
        assertEquals(ItemMatcher.Result.NO_MATCH, res.result);
    }

    @Test
    void emptyContentsArePartialWhenRecipesExist() {
        Recipe heal = r("heal", List.of(
                new Ingredient(IngredientKey.item(Material.REDSTONE), 1)
        ));
        ItemMatcher.MatchResult res = matcher.matchKeys(new HashMap<>(), List.of(heal));
        assertEquals(ItemMatcher.Result.PARTIAL, res.result);
    }

    @Test
    void potionTypeIsPartOfKey() {
        Recipe a = r("awk", List.of(
                new Ingredient(IngredientKey.potion(Material.POTION, PotionType.AWKWARD), 1)
        ));
        Map<IngredientKey, Integer> contents = new HashMap<>();
        contents.put(IngredientKey.potion(Material.POTION, PotionType.WATER), 1);

        ItemMatcher.MatchResult res = matcher.matchKeys(contents, List.of(a));
        assertEquals(ItemMatcher.Result.NO_MATCH, res.result);
    }

    @Test
    void nestedRecipeKeyMatchesNested() {
        Recipe tier2 = r("tier2", List.of(
                new Ingredient(IngredientKey.nested("phoenix_elixir"), 1),
                new Ingredient(IngredientKey.item(Material.GHAST_TEAR), 1)
        ));
        Map<IngredientKey, Integer> contents = new HashMap<>();
        contents.put(IngredientKey.nested("phoenix_elixir"), 1);
        contents.put(IngredientKey.item(Material.GHAST_TEAR), 1);

        ItemMatcher.MatchResult res = matcher.matchKeys(contents, List.of(tier2));
        assertEquals(ItemMatcher.Result.MATCH, res.result);
    }

    @Test
    void exactMatchBeatsPartialSuperset() {
        Recipe small = r("small", List.of(
                new Ingredient(IngredientKey.item(Material.REDSTONE), 1)
        ));
        Recipe big = r("big", List.of(
                new Ingredient(IngredientKey.item(Material.REDSTONE), 2)
        ));
        Map<IngredientKey, Integer> contents = new HashMap<>();
        contents.put(IngredientKey.item(Material.REDSTONE), 1);

        // small is exactly satisfied, big is partial. small must win.
        ItemMatcher.MatchResult res = matcher.matchKeys(contents, List.of(big, small));
        assertEquals(ItemMatcher.Result.MATCH, res.result);
        assertEquals("small", res.matched.id());
    }
}
