package dev.wndrxz.potioncombine.locale;

import dev.wndrxz.potioncombine.PotionCombine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class LocaleManager {

    private static final String[] SHIPPED = { "en", "ru" };

    private final PotionCombine plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final Set<String> missingKeysLogged = new HashSet<>();

    private YamlConfiguration messages;
    private YamlConfiguration fallback;
    private Locale resolved;

    public LocaleManager(PotionCombine plugin) {
        this.plugin = plugin;
    }

    public void load(Locale resolved) {
        this.resolved = resolved;
        this.fallback = readLang("en");
        String tag = resolved.getLanguage().toLowerCase(Locale.ROOT);
        if (tag.equals("en")) {
            this.messages = this.fallback;
        } else {
            this.messages = readLang(tag);
        }
        missingKeysLogged.clear();
    }

    public Locale locale() { return resolved; }

    public static String[] shippedLanguages() { return SHIPPED.clone(); }

    public Component get(String key, TagResolver... resolvers) {
        String raw = lookup(key);
        return mm.deserialize(raw, resolvers);
    }

    public Component prefixed(String key, TagResolver... resolvers) {
        return get("prefix").append(get(key, resolvers));
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        Audience a = (Audience) to;
        a.sendMessage(prefixed(key, resolvers));
    }

    /** Send a message to every player within {@code radius} blocks of a location.
     *  When the radius is zero or negative, nobody is notified. The cauldron
     *  itself is not a player, so we always go through online players. */
    public void broadcastNearby(org.bukkit.Location centre, int radius, String key, TagResolver... resolvers) {
        broadcastNearbyExcept(centre, radius, null, key, resolvers);
    }

    /** As {@link #broadcastNearby}, but skips {@code exclude} - used when that
     *  player was already messaged directly, so the area broadcast does not
     *  reach them a second time. */
    public void broadcastNearbyExcept(org.bukkit.Location centre, int radius,
                                      org.bukkit.entity.Player exclude,
                                      String key, TagResolver... resolvers) {
        if (centre == null || centre.getWorld() == null || radius <= 0) return;
        net.kyori.adventure.text.Component msg = prefixed(key, resolvers);
        double r2 = (double) radius * radius;
        for (org.bukkit.entity.Player p : centre.getWorld().getPlayers()) {
            if (exclude != null && p.getUniqueId().equals(exclude.getUniqueId())) continue;
            if (p.getLocation().distanceSquared(centre) <= r2) p.sendMessage(msg);
        }
    }

    public void sendPlain(CommandSender to, String key, TagResolver... resolvers) {
        Audience a = (Audience) to;
        a.sendMessage(get(key, resolvers));
    }

    public Component legacy(String legacyString) {
        if (legacyString == null) return Component.empty();
        return legacy.deserialize(legacyString);
    }

    public static TagResolver placeholder(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    public static TagResolver component(String name, Component value) {
        return Placeholder.component(name, value);
    }

    private String lookup(String key) {
        String s = messages != null ? messages.getString(key) : null;
        if (s != null) return s;
        s = fallback != null ? fallback.getString(key) : null;
        if (s != null) {
            if (missingKeysLogged.add(key)) {
                plugin.getLogger().fine("Missing locale key '" + key + "' — falling back to English.");
            }
            return s;
        }
        if (missingKeysLogged.add(key)) {
            plugin.getLogger().warning("Locale key '" + key + "' missing from both locales.");
        }
        return key;
    }

    private YamlConfiguration readLang(String tag) {
        File external = new File(plugin.getDataFolder(), "lang/" + tag + ".yml");
        if (!external.exists()) {
            try {
                external.getParentFile().mkdirs();
                try (InputStream in = plugin.getResource("lang/" + tag + ".yml")) {
                    if (in != null) Files.copy(in, external.toPath());
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not copy lang/" + tag + ".yml: " + ex.getMessage());
            }
        }
        if (external.exists()) {
            return YamlConfiguration.loadConfiguration(external);
        }
        InputStream in = plugin.getResource("lang/" + tag + ".yml");
        if (in == null) return new YamlConfiguration();
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read bundled lang/" + tag + ".yml: " + ex.getMessage());
            return new YamlConfiguration();
        }
    }
}
