package dev.wndrxz.potioncombine.command;

import dev.wndrxz.potioncombine.PotionCombine;
import dev.wndrxz.potioncombine.locale.LocaleManager;
import dev.wndrxz.potioncombine.recipe.Recipe;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PotionCombineCommand implements TabExecutor {

    private final PotionCombine plugin;

    public PotionCombineCommand(PotionCombine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> reload(sender);
            case "give"   -> give(sender, args);
            case "info"   -> info(sender);
            case "help"   -> sendHelp(sender);
            default       -> plugin.locale().send(sender, "command.unknown");
        }
        return true;
    }

    private void info(CommandSender sender) {
        plugin.locale().sendPlain(sender, "command.help_header");
        plugin.locale().sendPlain(sender, "command.info_version",
                LocaleManager.placeholder("version", plugin.getDescription().getVersion()));
        plugin.locale().sendPlain(sender, "command.info_recipes",
                LocaleManager.placeholder("count", String.valueOf(plugin.recipeManager().all().size())));
        int brewing = 0;
        for (var s : plugin.cauldronManager().all().values()) {
            if (s.state() == dev.wndrxz.potioncombine.cauldron.CauldronSession.State.BREWING) brewing++;
        }
        plugin.locale().sendPlain(sender, "command.info_brews",
                LocaleManager.placeholder("count", String.valueOf(brewing)));
        plugin.locale().sendPlain(sender, "command.info_papi",
                LocaleManager.placeholder("state",
                        plugin.placeholderHook().isRegistered() ? "ok" : "off"));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("potioncombine.admin")) {
            plugin.locale().send(sender, "command.no_permission");
            return;
        }
        plugin.reloadEverything();
        plugin.locale().send(sender, "command.reloaded");
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("potioncombine.admin")) {
            plugin.locale().send(sender, "command.no_permission");
            return;
        }
        if (args.length < 3) {
            plugin.locale().send(sender, "command.give_usage");
            return;
        }
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) {
            plugin.locale().send(sender, "command.give_player_not_found",
                    LocaleManager.placeholder("player", args[1]));
            return;
        }
        String recipeId = args[2];
        Recipe recipe = plugin.recipeManager().get(recipeId);
        if (recipe == null) {
            plugin.locale().send(sender, "command.give_recipe_not_found",
                    LocaleManager.placeholder("recipe", recipeId));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
            catch (NumberFormatException ignored) {}
        }
        ItemStack stack = plugin.recipeManager().produce(recipe);
        if (stack == null) return;
        // Potions don't stack, so issuing `amount` copies as one stack would
        // collapse to a single item. Hand them out one at a time.
        int given = 0;
        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(stack.clone());
            for (ItemStack o : overflow.values()) p.getWorld().dropItem(p.getLocation(), o);
            given++;
        }
        plugin.locale().send(sender, "command.give_success",
                LocaleManager.placeholder("amount", String.valueOf(given)),
                LocaleManager.placeholder("recipe", recipeId),
                LocaleManager.placeholder("player", p.getName()));
    }

    private void sendHelp(CommandSender sender) {
        plugin.locale().sendPlain(sender, "command.help_header");
        plugin.locale().sendPlain(sender, "command.help_help");
        plugin.locale().sendPlain(sender, "command.help_info");
        if (sender.hasPermission("potioncombine.admin")) {
            plugin.locale().sendPlain(sender, "command.help_reload");
            plugin.locale().sendPlain(sender, "command.help_give");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("help", "info", "reload", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            for (Recipe r : plugin.recipeManager().all()) ids.add(r.id());
            return filter(ids, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        }
        return out;
    }
}
