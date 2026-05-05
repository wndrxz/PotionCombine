package dev.wndrxz.potioncombine.recipe;

public final class Ingredient {

    private final IngredientKey key;
    private final int amount;

    public Ingredient(IngredientKey key, int amount) {
        if (amount < 1) throw new IllegalArgumentException("amount must be >= 1");
        this.key = key;
        this.amount = amount;
    }

    public IngredientKey key() { return key; }
    public int amount() { return amount; }

    @Override
    public String toString() {
        return amount + "x " + key;
    }
}
