package dev.wndrxz.potioncombine.display;

import dev.wndrxz.potioncombine.util.Particles;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class ParticleConfig {

    public record Spec(Particle particle, int count, double dx, double dy, double dz, double speed) {}

    private static final Map<String, String[]> ALIASES = Map.ofEntries(
            Map.entry("WITCH",            new String[]{"WITCH", "SPELL_WITCH"}),
            Map.entry("SPELL_WITCH",      new String[]{"WITCH", "SPELL_WITCH"}),
            Map.entry("BUBBLE_POP",       new String[]{"BUBBLE_POP"}),
            Map.entry("HAPPY",            new String[]{"HAPPY_VILLAGER", "VILLAGER_HAPPY"}),
            Map.entry("HAPPY_VILLAGER",   new String[]{"HAPPY_VILLAGER", "VILLAGER_HAPPY"}),
            Map.entry("VILLAGER_HAPPY",   new String[]{"HAPPY_VILLAGER", "VILLAGER_HAPPY"}),
            Map.entry("LARGE_SMOKE",      new String[]{"LARGE_SMOKE", "SMOKE_LARGE"}),
            Map.entry("SMOKE_LARGE",      new String[]{"LARGE_SMOKE", "SMOKE_LARGE"}),
            Map.entry("POOF",             new String[]{"POOF", "EXPLOSION_NORMAL"}),
            Map.entry("EXPLOSION_NORMAL", new String[]{"POOF", "EXPLOSION_NORMAL"}),
            Map.entry("DUST",             new String[]{"DUST", "REDSTONE"}),
            Map.entry("REDSTONE",         new String[]{"DUST", "REDSTONE"})
    );

    private boolean enabled = true;
    private final Map<String, List<Spec>> events = new HashMap<>();

    public void load(ConfigurationSection section, Logger logger) {
        events.clear();
        if (section == null) {
            enabled = true;
            applyDefaults();
            return;
        }
        enabled = section.getBoolean("enabled", true);
        ConfigurationSection evs = section.getConfigurationSection("events");
        if (evs == null) {
            applyDefaults();
            return;
        }
        for (String key : evs.getKeys(false)) {
            List<Spec> specs = new ArrayList<>();
            List<Map<?, ?>> list = evs.getMapList(key);
            if (list.isEmpty()) {
                // Allow a single object form too.
                ConfigurationSection single = evs.getConfigurationSection(key);
                if (single != null) {
                    Spec s = parseSpec(single.getValues(false), key, logger);
                    if (s != null) specs.add(s);
                }
            } else {
                for (Map<?, ?> raw : list) {
                    Spec s = parseSpec(raw, key, logger);
                    if (s != null) specs.add(s);
                }
            }
            events.put(key.toLowerCase(Locale.ROOT), specs);
        }
        // Fill in any events the user did not define with defaults so 1.0
        // visuals are preserved on upgrade.
        applyDefaultsForMissing();
    }

    public boolean enabled() { return enabled; }

    public void play(String event, World world, Location at) {
        if (!enabled || world == null || at == null) return;
        List<Spec> specs = events.get(event);
        if (specs == null) return;
        for (Spec s : specs) {
            if (s.particle == null) continue;
            world.spawnParticle(s.particle, at, s.count, s.dx, s.dy, s.dz, s.speed);
        }
    }

    /** Iterate over the specs for an event without playing them — used by
     *  callers that need to add custom particle data (dust colour, etc.). */
    public void forEach(String event, Consumer<Spec> sink) {
        if (!enabled) return;
        List<Spec> specs = events.get(event);
        if (specs == null) return;
        for (Spec s : specs) sink.accept(s);
    }

    private Spec parseSpec(Map<?, ?> raw, String event, Logger logger) {
        Object nameObj = raw.get("name");
        if (nameObj == null) {
            logger.warning("Particle entry under '" + event + "' is missing 'name', skipped.");
            return null;
        }
        String name = nameObj.toString().trim().toUpperCase(Locale.ROOT);
        Particle p = resolveParticle(name);
        if (p == null) {
            logger.warning("Particle '" + name + "' under '" + event + "' is not available on this server, skipped.");
            return null;
        }
        int count   = asInt(raw.get("count"),     8);
        double dx   = asDouble(raw.get("offset_x"), 0.2);
        double dy   = asDouble(raw.get("offset_y"), 0.15);
        double dz   = asDouble(raw.get("offset_z"), 0.2);
        double sp   = asDouble(raw.get("speed"),    0.0);
        return new Spec(p, Math.max(0, count), dx, dy, dz, sp);
    }

    private static Particle resolveParticle(String name) {
        String[] aliases = ALIASES.getOrDefault(name, new String[]{name});
        for (String alias : aliases) {
            try { return Particle.valueOf(alias); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        return def;
    }

    private static double asDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        return def;
    }

    private void applyDefaults() {
        events.put("insert",         List.of(new Spec(Particles.POOF, 6, 0.2, 0.1, 0.2, 0.01)));
        // Bubbles only by default. WITCH sparkles are reserved for brew_start
        // — at five times per second they fight with the bubbles for screen.
        events.put("brewing_bubble", List.of(
                new Spec(Particles.BUBBLE_POP, 6, 0.2, 0.1, 0.2, 0.05)));
        events.put("brew_start",     List.of(new Spec(Particles.WITCH, 12, 0.4, 0.3, 0.4, 0.02)));
        events.put("brew_success",   List.of(new Spec(Particles.HAPPY, 24, 0.45, 0.4, 0.45, 0.0)));
        events.put("brew_fail",      List.of(new Spec(Particles.LARGE_SMOKE, 18, 0.35, 0.2, 0.35, 0.02)));
        events.put("collect",        List.of(new Spec(Particles.POOF, 8, 0.2, 0.2, 0.2, 0.0)));
        events.put("pollution_idle", List.of(new Spec(Particles.LARGE_SMOKE, 2, 0.18, 0.1, 0.18, 0.005)));
        events.put("cleaning_brush", List.of(new Spec(Particles.POOF, 8, 0.25, 0.15, 0.25, 0.01)));
    }

    private void applyDefaultsForMissing() {
        Map<String, List<Spec>> defaults = new HashMap<>();
        ParticleConfig probe = new ParticleConfig();
        probe.applyDefaults();
        defaults.putAll(probe.events);
        for (Map.Entry<String, List<Spec>> e : defaults.entrySet()) {
            events.putIfAbsent(e.getKey(), e.getValue());
        }
    }
}
