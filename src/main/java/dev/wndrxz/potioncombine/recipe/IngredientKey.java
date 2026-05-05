package dev.wndrxz.potioncombine.recipe;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.Objects;

/**
 * Canonical matching identity for one ingredient — what makes two items
 * "the same" for shapeless recipe matching. Three mutually-exclusive shapes:
 *  - a nested custom potion: discriminated by recipeId only;
 *  - a vanilla potion variant: discriminated by Material + PotionType;
 *  - everything else: discriminated by Material alone.
 */
public final class IngredientKey {

    private final Material material;
    private final PotionType potionType;
    private final String recipeId;

    private IngredientKey(Material material, PotionType potionType, String recipeId) {
        this.material = material;
        this.potionType = potionType;
        this.recipeId = recipeId;
    }

    public static IngredientKey nested(String recipeId) {
        return new IngredientKey(null, null, Objects.requireNonNull(recipeId));
    }

    public static IngredientKey potion(Material material, PotionType type) {
        return new IngredientKey(Objects.requireNonNull(material), Objects.requireNonNull(type), null);
    }

    public static IngredientKey item(Material material) {
        return new IngredientKey(Objects.requireNonNull(material), null, null);
    }

    public Material material() { return material; }
    public PotionType potionType() { return potionType; }
    public String recipeId() { return recipeId; }

    public boolean isNested() { return recipeId != null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IngredientKey k)) return false;
        return Objects.equals(material, k.material)
            && Objects.equals(potionType, k.potionType)
            && Objects.equals(recipeId, k.recipeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, potionType, recipeId);
    }

    @Override
    public String toString() {
        if (recipeId != null) return "nested:" + recipeId;
        if (potionType != null) return material.name() + "[" + potionType.name() + "]";
        return material.name();
    }
}
