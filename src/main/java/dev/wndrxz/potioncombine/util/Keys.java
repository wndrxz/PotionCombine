package dev.wndrxz.potioncombine.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {

    public static NamespacedKey RECIPE_ID;
    public static NamespacedKey RECIPE_VERSION;
    public static NamespacedKey OWNED_ENTITY;

    private Keys() {}

    public static void init(Plugin plugin) {
        RECIPE_ID      = new NamespacedKey(plugin, "recipe_id");
        RECIPE_VERSION = new NamespacedKey(plugin, "version");
        OWNED_ENTITY   = new NamespacedKey(plugin, "owned");
    }
}
