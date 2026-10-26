package dev.kwlew.haven.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The text-level half of a config upgrade: lays an admin's customised values over a newer bundled
 * default, without touching any I/O.
 * <p>
 * It works on the default's <em>text</em> rather than loading and re-saving a
 * {@link YamlConfiguration}, because Paper 1.17.1 has no comment support: a round trip through
 * Bukkit would strip every comment, including the new ones that are the whole point of upgrading.
 * So the default is walked line by line, each leaf key is located, and only the leaves the admin
 * really changed are replaced.
 * <p>
 * "Really changed" means different from the <em>previous</em> version's default. A value the
 * admin never touched equals the old default, so it is dropped in favour of the new default text;
 * that is how a redesigned message reaches servers that never edited it, while a customised one
 * survives.
 * <p>
 * The format understood is the one the bundled files use: block mappings, scalar values, flow
 * {@code []} / {@code {}}, and block lists of {@code - item} lines. Anything else (block scalars,
 * stray list items) throws, because guessing at a layout could corrupt the file; the caller
 * treats a throw as "leave the admin's file alone".
 */
final class YamlMerge {

    /** Marker key; never copied from the admin's file, so the output always carries the new one. */
    static final String VERSION_KEY = "config-version";

    private static final String TEMP_KEY = "tmp";
    private static final String STEP = "  ";

    private YamlMerge() {}

    /**
     * @param text    the merged file
     * @param applied path to value for every admin value that was written, for verification
     * @param dropped admin paths the new default has no place for and that the admin had changed
     */
    record Result(String text, Map<String, Object> applied, List<String> dropped) {}

    /**
     * @param newDefaultText the bundled default for the new version, verbatim
     * @param oldDefaults    the previous version's default, to tell customisations from leftovers
     * @param adminValues    the admin's current file
     */
    static Result merge(String newDefaultText, ConfigurationSection oldDefaults, ConfigurationSection adminValues) {
        List<String> lines = new ArrayList<>(List.of(newDefaultText.replace("\r\n", "\n").split("\n", -1)));
        Layout layout = Layout.parse(lines);

        List<String> out = new ArrayList<>();
        Map<String, Object> applied = new LinkedHashMap<>();
        int cursor = 0;

        for (Leaf leaf : layout.leaves) {
            if (leaf.path.equals(VERSION_KEY)) {
                continue;
            }

            Object admin = normalize(adminValues.get(leaf.path));
            if (admin == null || admin.equals(normalize(oldDefaults.get(leaf.path)))) {
                continue;
            }

            out.addAll(lines.subList(cursor, leaf.start));
            emit(out, " ".repeat(leaf.indent), leaf.rawKey + ":", admin);
            applied.put(leaf.path, admin);
            cursor = leaf.end;
        }

        out.addAll(lines.subList(cursor, lines.size()));

        List<String> dropped = new ArrayList<>();
        collectDropped("", adminValues, oldDefaults, layout, dropped);

        return new Result(String.join("\n", out), applied, dropped);
    }

    /**
     * Admin options with no home in the new default. An untouched leftover (equal to the old
     * default) is skipped quietly: reporting every message a release removed would bury the
     * options an admin actually cares about losing.
     */
    private static void collectDropped(String prefix, ConfigurationSection admin, ConfigurationSection old,
                                       Layout layout, List<String> dropped) {
        for (String key : admin.getKeys(false)) {
            String path = prefix + key;

            if (path.equals(VERSION_KEY) || layout.leafPaths.contains(path)) {
                continue;
            }

            Object value = admin.get(key);

            if (layout.sectionPaths.contains(path) && value instanceof ConfigurationSection section) {
                collectDropped(path + ".", section, old, layout, dropped);
            } else if (!Objects.equals(normalize(value), normalize(old.get(path)))) {
                dropped.add(path);
            }
        }
    }

    /**
     * Turns Bukkit sections into plain maps (recursively) so a value read from one file compares
     * equal to the same value read from another.
     */
    static Object normalize(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> map = new LinkedHashMap<>();

            for (String key : section.getKeys(false)) {
                map.put(key, normalize(section.get(key)));
            }

            return map;
        }

        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();

            for (Object element : list) {
                copy.add(normalize(element));
            }

            return copy;
        }

        return value;
    }

    // ---- writing values ----

    /**
     * Writes {@code key value} at the given indent. Containers are laid out here and only scalars
     * go through Bukkit, since Bukkit's own dump wraps long strings across lines at 80 columns,
     * which is legal YAML but makes a message in messages.yml unreadable.
     */
    private static void emit(List<String> out, String indent, String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.add(indent + key + " {}");
                return;
            }

            out.add(indent + key);

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                emit(out, indent + STEP, scalar(entry.getKey()) + ":", entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.add(indent + key + " []");
                return;
            }

            out.add(indent + key);

            for (Object element : list) {
                if (element instanceof Map<?, ?> || element instanceof List<?>) {
                    throw new UnsupportedOperationException("nested collection inside a list at " + key);
                }

                out.add(indent + STEP + "- " + scalar(element));
            }
        } else {
            out.add(indent + key + " " + scalar(value));
        }
    }

    /**
     * One scalar as YAML text, quoted exactly as Bukkit would quote it. Bukkit decides when quotes
     * are needed (a {@code :} in a message, a leading {@code <}, text that looks like a number),
     * which is the part that is easy to get subtly wrong by hand.
     */
    private static String scalar(Object value) {
        if (value == null) {
            return "null";
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(TEMP_KEY, value);

        String prefix = TEMP_KEY + ": ";
        String dump = yaml.saveToString().strip();

        if (dump.startsWith(prefix) && dump.indexOf('\n') < 0) {
            return dump.substring(prefix.length());
        }

        if (value instanceof String text) {
            // Wrapped across lines by the dumper. A double-quoted single line says the same thing.
            return doubleQuote(text);
        }

        throw new UnsupportedOperationException("cannot write " + value.getClass().getSimpleName() + " value");
    }

    private static String doubleQuote(String text) {
        StringBuilder quoted = new StringBuilder("\"");

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            switch (c) {
                case '\\' -> quoted.append("\\\\");
                case '"' -> quoted.append("\\\"");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }

        return quoted.append('"').toString();
    }

    // ---- reading the default's layout ----

    /**
     * A leaf option in the default text: lines {@code [start, end)} hold its key and whole value.
     */
    private record Leaf(String path, String rawKey, int indent, int start, int end) {}

    private record Layout(List<Leaf> leaves, Set<String> leafPaths, Set<String> sectionPaths) {

        static Layout parse(List<String> lines) {
            List<Leaf> leaves = new ArrayList<>();
            Set<String> sections = new HashSet<>();
            List<String> keys = new ArrayList<>();
            List<Integer> indents = new ArrayList<>();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                if (isBlankOrComment(line)) {
                    continue;
                }

                int indent = indentOf(line);
                String trimmed = line.strip();

                if (isListItem(trimmed)) {
                    throw new IllegalArgumentException("unexpected list item on line " + (i + 1));
                }

                while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                    indents.remove(indents.size() - 1);
                    keys.remove(keys.size() - 1);
                }

                int colon = keyEnd(trimmed);

                if (colon < 0) {
                    throw new IllegalArgumentException("expected a key on line " + (i + 1));
                }

                String rawKey = trimmed.substring(0, colon);
                String key = unquote(rawKey);
                String path = keys.isEmpty() ? key : String.join(".", keys) + "." + key;
                String rest = trimmed.substring(colon + 1).strip();

                if (rest.startsWith("|") || rest.startsWith(">")) {
                    throw new IllegalArgumentException("block scalar on line " + (i + 1));
                }

                if (!rest.isEmpty() && !rest.startsWith("#")) {
                    leaves.add(new Leaf(path, rawKey, indent, i, i + 1));
                    continue;
                }

                int next = nextContent(lines, i + 1);

                if (next >= 0 && isListItem(lines.get(next).strip()) && indentOf(lines.get(next)) >= indent) {
                    int end = i + 1;

                    for (int j = next; j >= 0; j = nextContent(lines, j + 1)) {
                        String candidate = lines.get(j);

                        if ((isListItem(candidate.strip()) && indentOf(candidate) >= indent)
                                || indentOf(candidate) > indent) {
                            end = j + 1;
                        } else {
                            break;
                        }
                    }

                    leaves.add(new Leaf(path, rawKey, indent, i, end));
                    i = end - 1;
                } else if (next >= 0 && indentOf(lines.get(next)) > indent) {
                    sections.add(path);
                    keys.add(key);
                    indents.add(indent);
                } else {
                    leaves.add(new Leaf(path, rawKey, indent, i, i + 1));
                }
            }

            Set<String> leafPaths = new HashSet<>();
            leaves.forEach(leaf -> leafPaths.add(leaf.path));

            return new Layout(leaves, leafPaths, sections);
        }

        private static int nextContent(List<String> lines, int from) {
            for (int i = from; i < lines.size(); i++) {
                if (!isBlankOrComment(lines.get(i))) {
                    return i;
                }
            }

            return -1;
        }

        private static boolean isBlankOrComment(String line) {
            String trimmed = line.strip();

            return trimmed.isEmpty() || trimmed.startsWith("#");
        }

        private static boolean isListItem(String trimmed) {
            return trimmed.equals("-") || trimmed.startsWith("- ");
        }

        private static int indentOf(String line) {
            int indent = 0;

            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }

            return indent;
        }

        /** Index of the colon ending the key, or -1. Quoted keys may contain colons. */
        private static int keyEnd(String trimmed) {
            int from = 0;

            if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
                from = trimmed.indexOf(trimmed.charAt(0), 1);

                if (from < 0) {
                    return -1;
                }
            }

            for (int i = from; i < trimmed.length(); i++) {
                if (trimmed.charAt(i) == ':' && (i + 1 == trimmed.length() || trimmed.charAt(i + 1) == ' ')) {
                    return i;
                }
            }

            return -1;
        }

        private static String unquote(String rawKey) {
            if (rawKey.length() >= 2 && (rawKey.startsWith("\"") || rawKey.startsWith("'"))
                    && rawKey.endsWith(rawKey.substring(0, 1))) {
                return rawKey.substring(1, rawKey.length() - 1);
            }

            return rawKey;
        }
    }
}
