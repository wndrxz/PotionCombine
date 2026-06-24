package dev.wndrxz.potioncombine.journal;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.locale.LocaleManager;
import dev.wndrxz.potioncombine.progress.PlayerProgress;
import dev.wndrxz.potioncombine.recipe.Ingredient;
import dev.wndrxz.potioncombine.recipe.IngredientKey;
import dev.wndrxz.potioncombine.recipe.Recipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The alchemist's journal — a written book opened in the player's hand. Three
 * modes, exactly as the roadmap framed them:
 *
 *   discovery  — the front of the book. How many of the loaded recipes you've
 *                brewed, and which ones, with the rest left as locked mysteries
 *                so the journal is a thing you fill in by playing.
 *   reference  — one page per discovered recipe listing what goes into it. A
 *                recipe you've never brewed shows nothing here; that is the
 *                point. (With progression off, every recipe is "discovered" so
 *                the reference works as a plain cookbook.)
 *   lab notes  — your running counters: total brews, spoiled, failed, and your
 *                three most-brewed recipes.
 *
 * Everything legible through MiniMessage-free legacy text because written-book
 * pages render plain components; the recipe display names keep their own colour
 * via the legacy serializer the rest of the plugin already uses.
 */
public final class JournalManager {

    public enum Mode { DISCOVERY, REFERENCE, NOTES }

    private final PotionCombine plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public JournalManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    /** Hand the player a freshly-built journal book and open it. The book is
     *  not added to the inventory — it is a throwaway view, the same trick
     *  the vanilla "open book" packet uses, so it never clutters a hotbar. */
    public void open(Player player, Mode mode) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        meta.title(plugin.locale().get("journal.title"));
        meta.author(Component.text("PotionCombine"));

        PlayerProgress progress = plugin.progressManager().of(player.getUniqueId());
        List<Component> pages = switch (mode) {
            case DISCOVERY -> discoveryPages(progress);
            case REFERENCE -> referencePages(progress);
            case NOTES     -> notesPages(progress);
        };
        if (pages.isEmpty()) pages.add(plugin.locale().get("journal.empty"));
        meta.pages(pages);

        book.setItemMeta(meta);
        player.openBook(book);
    }

    // A written-book page only fits a dozen-odd lines before vanilla starts
    // dropping them. Keep the recipe list to a comfortable run per page and
    // spill onto the next, so a server with a full 20-recipe book still reads
    // every entry instead of silently truncating.
    private static final int ENTRIES_PER_PAGE = 12;

    private List<Component> discoveryPages(PlayerProgress progress) {
        List<Component> pages = new ArrayList<>();
        int total = plugin.recipeManager().all().size();
        int found = countDiscovered(progress);

        Component head = plugin.locale().get("journal.discovery_header",
                LocaleManager.placeholder("found", Integer.toString(found)),
                LocaleManager.placeholder("total", Integer.toString(total)));

        // A recipe per line — known ones in their own colour, the rest a row
        // of question marks so the book reads as a thing still being filled in.
        Component locked = plugin.locale().get("journal.locked_entry");
        Component page = head;
        int onPage = 0;
        for (Recipe r : plugin.recipeManager().all()) {
            if (onPage >= ENTRIES_PER_PAGE) {
                pages.add(page);
                page = head;
                onPage = 0;
            }
            Component line = isDiscovered(progress, r.id())
                    ? Component.text("• ").append(legacy.deserialize(r.displayNameLegacy()))
                    : Component.text("• ").append(locked);
            page = page.append(Component.newline()).append(line);
            onPage++;
        }
        pages.add(page);
        return pages;
    }

    private List<Component> referencePages(PlayerProgress progress) {
        List<Component> pages = new ArrayList<>();
        for (Recipe r : plugin.recipeManager().all()) {
            if (!isDiscovered(progress, r.id())) continue;
            Component page = legacy.deserialize(r.displayNameLegacy())
                    .append(Component.newline())
                    .append(plugin.locale().get("journal.reference_needs"));
            for (Map.Entry<IngredientKey, Integer> e : aggregate(r).entrySet()) {
                page = page.append(Component.newline())
                        .append(Component.text("  " + e.getValue() + "x "))
                        .append(ingredientLabel(e.getKey()));
            }
            pages.add(page);
        }
        return pages;
    }

    private List<Component> notesPages(PlayerProgress progress) {
        List<Component> pages = new ArrayList<>();
        Component page = plugin.locale().get("journal.notes_header")
                .append(Component.newline())
                .append(plugin.locale().get("journal.notes_brews",
                        LocaleManager.placeholder("count", Integer.toString(progress.totalBrews()))))
                .append(Component.newline())
                .append(plugin.locale().get("journal.notes_spoiled",
                        LocaleManager.placeholder("count", Integer.toString(progress.totalSpoiled()))))
                .append(Component.newline())
                .append(plugin.locale().get("journal.notes_failed",
                        LocaleManager.placeholder("count", Integer.toString(progress.totalFailed()))));

        List<Map.Entry<String, Integer>> top = topBrews(progress, 3);
        if (!top.isEmpty()) {
            page = page.append(Component.newline()).append(Component.newline())
                    .append(plugin.locale().get("journal.notes_favourites"));
            for (Map.Entry<String, Integer> e : top) {
                Recipe r = plugin.recipeManager().get(e.getKey());
                Component name = r != null
                        ? legacy.deserialize(r.displayNameLegacy())
                        : Component.text(e.getKey());
                page = page.append(Component.newline())
                        .append(Component.text("  " + e.getValue() + "x ")).append(name);
            }
        }
        pages.add(page);
        return pages;
    }

    /** Progression-off behaviour: treat everything as discovered so the
     *  reference is a plain cookbook and the discovery list is complete. */
    private boolean isDiscovered(PlayerProgress progress, String recipeId) {
        return !plugin.progressManager().enabled() || progress.hasDiscovered(recipeId);
    }

    private int countDiscovered(PlayerProgress progress) {
        if (!plugin.progressManager().enabled()) return plugin.recipeManager().all().size();
        int n = 0;
        for (Recipe r : plugin.recipeManager().all()) {
            if (progress.hasDiscovered(r.id())) n++;
        }
        return n;
    }

    /** A recipe's ingredient multiset, keeping the recipes.yml order so the
     *  reference page reads the way the operator wrote it. */
    private Map<IngredientKey, Integer> aggregate(Recipe r) {
        java.util.LinkedHashMap<IngredientKey, Integer> out = new java.util.LinkedHashMap<>();
        for (Ingredient ing : r.ingredients()) out.merge(ing.key(), ing.amount(), Integer::sum);
        return out;
    }

    private Component ingredientLabel(IngredientKey key) {
        if (key.isNested()) {
            Recipe nested = plugin.recipeManager().get(key.recipeId());
            if (nested != null) return legacy.deserialize(nested.displayNameLegacy());
            return Component.text(key.recipeId());
        }
        String base = key.material().name().toLowerCase().replace('_', ' ');
        if (key.potionType() != null) {
            return Component.text(base + " (" + key.potionType().name().toLowerCase() + ")");
        }
        return Component.text(base);
    }

    private List<Map.Entry<String, Integer>> topBrews(PlayerProgress progress, int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(progress.brewCounts().entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
    }
}
