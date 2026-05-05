package dev.wndrxz.potioncombine.recipe;

import org.bukkit.Color;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Recipe {

    private final String id;
    private final String displayNameLegacy;
    private final List<String> loreLegacy;
    private final int brewTimeTicks;
    private final Color color;
    private final PotionType basePotion;
    private final List<Ingredient> ingredients;
    private final List<PotionEffect> effects;

    private final Map<IngredientKey, Integer> required;
    private final int totalRequired;

    public Recipe(String id,
                  String displayNameLegacy,
                  List<String> loreLegacy,
                  int brewTimeTicks,
                  Color color,
                  PotionType basePotion,
                  List<Ingredient> ingredients,
                  List<PotionEffect> effects) {
        this.id = id;
        this.displayNameLegacy = displayNameLegacy;
        this.loreLegacy = loreLegacy == null ? List.of() : List.copyOf(loreLegacy);
        this.brewTimeTicks = brewTimeTicks;
        this.color = color;
        this.basePotion = basePotion;
        this.ingredients = List.copyOf(ingredients);
        this.effects = effects == null ? List.of() : List.copyOf(effects);

        Map<IngredientKey, Integer> req = new HashMap<>();
        int total = 0;
        for (Ingredient ing : this.ingredients) {
            req.merge(ing.key(), ing.amount(), Integer::sum);
            total += ing.amount();
        }
        this.required = Collections.unmodifiableMap(req);
        this.totalRequired = total;
    }

    public String id() { return id; }
    public String displayNameLegacy() { return displayNameLegacy; }
    public List<String> loreLegacy() { return loreLegacy; }
    public int brewTimeTicks() { return brewTimeTicks; }
    public Color color() { return color; }
    public PotionType basePotion() { return basePotion; }
    public List<Ingredient> ingredients() { return ingredients; }
    public List<PotionEffect> effects() { return effects; }

    public Map<IngredientKey, Integer> required() { return required; }
    public int totalRequired() { return totalRequired; }
}
