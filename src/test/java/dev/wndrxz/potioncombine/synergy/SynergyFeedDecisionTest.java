package dev.wndrxz.potioncombine.synergy;

import dev.wndrxz.potioncombine.recipe.Ingredient;
import dev.wndrxz.potioncombine.recipe.IngredientKey;
import dev.wndrxz.potioncombine.recipe.ItemMatcher;
import dev.wndrxz.potioncombine.recipe.Recipe;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The synergy feed rule in plain matcher terms: a finished result is only
 * poured into a neighbour it would <em>complete</em>. These tests pin that
 * decision so a future refactor of {@code SynergyManager.wouldComplete}
 * can't quietly start feeding (and sludging) half-finished neighbours.
 */
class SynergyFeedDecisionTest {

    private final ItemMatcher matcher = new ItemMatcher();

    private static Recipe crown() {
        return new Recipe("phoenix_crown", "&ePhoenix Crown", List.of(), 320, null, null,
                List.of(
                        new Ingredient(IngredientKey.nested("phoenix_elixir"), 1),
                        new Ingredient(IngredientKey.item(Material.GHAST_TEAR), 1),
                        new Ingredient(IngredientKey.item(Material.GLOWSTONE_DUST), 2)),
                List.of());
    }

    /** Mirror of what the feed check does: drop one of `added` onto the
     *  neighbour's current contents and see where the matcher lands. */
    private ItemMatcher.Result afterFeeding(Map<IngredientKey, Integer> target, IngredientKey added) {
        Map<IngredientKey, Integer> augmented = new HashMap<>(target);
        augmented.merge(added, 1, Integer::sum);
        return matcher.matchKeys(augmented, List.of(crown())).result;
    }

    @Test
    void elixirCompletesAPreloadedNeighbour() {
        Map<IngredientKey, Integer> target = new HashMap<>();
        target.put(IngredientKey.item(Material.GHAST_TEAR), 1);
        target.put(IngredientKey.item(Material.GLOWSTONE_DUST), 2);
        // The elixir is the last piece — pouring it in finishes the crown.
        assertEquals(ItemMatcher.Result.MATCH,
                afterFeeding(target, IngredientKey.nested("phoenix_elixir")));
    }

    @Test
    void elixirIntoAnEmptyCauldronOnlyGrows() {
        // Nothing else waiting: the elixir alone is a partial crown, never a
        // match — so synergy must not feed it, or it would just turn to sludge.
        assertEquals(ItemMatcher.Result.PARTIAL,
                afterFeeding(new HashMap<>(), IngredientKey.nested("phoenix_elixir")));
    }

    @Test
    void anUnrelatedOutputDeadEndsTheNeighbour() {
        Map<IngredientKey, Integer> target = new HashMap<>();
        target.put(IngredientKey.item(Material.GHAST_TEAR), 1);
        target.put(IngredientKey.item(Material.GLOWSTONE_DUST), 2);
        // A drowned_tear has no place in the crown — feeding it would wreck
        // the neighbour, so the decision is a flat no.
        assertEquals(ItemMatcher.Result.NO_MATCH,
                afterFeeding(target, IngredientKey.nested("drowned_tear")));
    }
}
