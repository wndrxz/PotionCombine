package dev.wndrxz.potioncombine.locale;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the bundled locale files honest. A missing key doesn't crash
 * anything (LocaleManager falls back and logs), but the player sees the raw
 * key in chat — which is exactly what happened when 1.3.0 shipped ru.yml
 * with command.help_journal and en.yml without it.
 *
 * Deliberately dependency-free: the lang files are two levels deep with
 * quoted one-line values, so a tiny indent walker is all the YAML parsing
 * we need here. If the files ever grow a third level, this parser follows
 * along for free; multi-line values would need a rethink.
 */
final class LocaleParityTest {

    @Test
    void bundledLocalesDefineExactlyTheSameKeys() {
        Set<String> en = keysOf("en");
        Set<String> ru = keysOf("ru");

        Set<String> missingInEn = new TreeSet<>(ru);
        missingInEn.removeAll(en);
        Set<String> missingInRu = new TreeSet<>(en);
        missingInRu.removeAll(ru);

        assertTrue(missingInEn.isEmpty(), "en.yml is missing: " + missingInEn);
        assertTrue(missingInRu.isEmpty(), "ru.yml is missing: " + missingInRu);
    }

    @Test
    void everyValueHasActualText() {
        for (String lang : new String[] {"en", "ru"}) {
            for (Map.Entry<String, String> e : entriesOf(lang).entrySet()) {
                // In config an empty string means "silent"; in a lang file a
                // blank message is always a mistake.
                assertFalse(e.getValue().isBlank(),
                        lang + ".yml has a blank value for " + e.getKey());
            }
        }
    }

    @Test
    void helpListCoversEveryHelpLine() {
        // The bug that motivated this class: /pc help renders one line per
        // command.help_* key, so a key missing from one locale shows up as
        // raw text in chat. Pin the full set by name.
        for (String lang : new String[] {"en", "ru"}) {
            Set<String> keys = keysOf(lang);
            for (String help : new String[] {
                    "command.help_header", "command.help_reload", "command.help_give",
                    "command.help_help", "command.help_info", "command.help_journal"}) {
                assertTrue(keys.contains(help), lang + ".yml is missing " + help);
            }
        }
    }

    private static Set<String> keysOf(String lang) {
        return new TreeSet<>(entriesOf(lang).keySet());
    }

    /** Walks a two-space-indented YAML file and flattens it to dotted keys.
     *  Comments and blank lines are skipped; surrounding quotes come off. */
    private static Map<String, String> entriesOf(String lang) {
        String resource = "/lang/" + lang + ".yml";
        InputStream in = LocaleParityTest.class.getResourceAsStream(resource);
        assertNotNull(in, resource + " is not on the test classpath");

        Map<String, String> out = new LinkedHashMap<>();
        List<String> path = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String stripped = line.strip();
                if (stripped.isEmpty() || stripped.startsWith("#")) continue;

                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') indent++;
                int depth = indent / 2;

                int colon = stripped.indexOf(':');
                if (colon < 0) continue; // not a mapping line; lang files have none
                String key = stripped.substring(0, colon).strip();
                String value = stripped.substring(colon + 1).strip();

                while (path.size() > depth) path.remove(path.size() - 1);
                path.add(key);

                if (!value.isEmpty()) {
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    out.put(String.join(".", path), value);
                    path.remove(path.size() - 1);
                }
            }
        } catch (IOException ex) {
            throw new AssertionError("Could not read " + resource, ex);
        }
        return out;
    }
}
