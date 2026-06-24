package dev.wndrxz.potioncombine.progress;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One alchemist's running tally. What they've discovered (so the journal can
 * keep the rest a mystery), how many times each recipe has brewed under their
 * hand, and the headline counters the lab-notes page reads off.
 *
 * Mutated only on the main thread through {@link ProgressManager}; nothing here
 * is synchronised. Discovery is monotonic — a recipe is never un-discovered, so
 * a recipes.yml that loses an entry simply stops showing it without erasing the
 * fact that this player once brewed it.
 */
public final class PlayerProgress {

    private final Set<String> discovered = new LinkedHashSet<>();
    private final Map<String, Integer> brewCounts = new LinkedHashMap<>();
    private int totalBrews;
    private int totalSpoiled;
    private int totalFailed;

    public boolean hasDiscovered(String recipeId) {
        return recipeId != null && discovered.contains(recipeId);
    }

    /** Mark a recipe seen. Returns true only the first time — the caller uses
     *  that to fire the one-shot "new recipe" feedback. */
    public boolean discover(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) return false;
        return discovered.add(recipeId);
    }

    public int brewCount(String recipeId) {
        Integer v = brewCounts.get(recipeId);
        return v == null ? 0 : v;
    }

    public void recordBrew(String recipeId) {
        if (recipeId != null && !recipeId.isBlank()) {
            brewCounts.merge(recipeId, 1, Integer::sum);
        }
        totalBrews++;
    }

    public void recordSpoiled() { totalSpoiled++; }
    public void recordFailed()  { totalFailed++; }

    public int discoveredCount() { return discovered.size(); }
    public int totalBrews()      { return totalBrews; }
    public int totalSpoiled()    { return totalSpoiled; }
    public int totalFailed()     { return totalFailed; }

    public Set<String> discovered() { return Collections.unmodifiableSet(discovered); }
    public Map<String, Integer> brewCounts() { return Collections.unmodifiableMap(brewCounts); }

    // ── Restore from disk. Bypasses the "first time" return of discover() so
    //    loading never reports a stale recipe as freshly found. ──────────────

    public void restoreDiscovered(String recipeId) {
        if (recipeId != null && !recipeId.isBlank()) discovered.add(recipeId);
    }

    public void restoreBrewCount(String recipeId, int count) {
        if (recipeId != null && !recipeId.isBlank() && count > 0) brewCounts.put(recipeId, count);
    }

    public void restoreTotals(int brews, int spoiled, int failed) {
        this.totalBrews   = Math.max(0, brews);
        this.totalSpoiled = Math.max(0, spoiled);
        this.totalFailed  = Math.max(0, failed);
    }

    public boolean isEmpty() {
        return discovered.isEmpty() && brewCounts.isEmpty()
                && totalBrews == 0 && totalSpoiled == 0 && totalFailed == 0;
    }
}
