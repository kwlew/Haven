package dev.kwlew.haven.managers.home;

import dev.kwlew.haven.managers.message.Messages;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Grouping and display names for worlds, shared by the chat list and the menu. */
public final class WorldNames {

    private WorldNames() {}

    /**
     * Admin override in {@code world-names}, then vanilla dimension names (only when the main world is loaded), then a
     * prettified folder name.
     */
    public static String displayName(Messages messages, String worldName) {
        String override = messages.raw("world-names." + worldName);

        if (override != null && !override.isBlank()) {
            return override;
        }

        if (Bukkit.getWorld(worldName) != null && !Bukkit.getWorlds().isEmpty()) {
            String main = Bukkit.getWorlds().get(0).getName();

            if (worldName.equals(main)) {
                return "Overworld";
            }
            if (worldName.equals(main + "_nether")) {
                return "The Nether";
            }
            if (worldName.equals(main + "_the_end")) {
                return "The End";
            }
        }

        return prettify(worldName);
    }

    static String prettify(String worldName) {
        StringBuilder pretty = new StringBuilder();

        for (String word : worldName.split("[_\\-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (pretty.length() > 0) {
                pretty.append(' ');
            }
            pretty.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }

        return pretty.length() == 0 ? worldName : pretty.toString();
    }

    /** Sorted by world then home name, so the list is stable between calls. */
    public static Map<String, List<Home>> groupByWorld(PlayerHomes homes) {
        Map<String, List<Home>> byWorld = new TreeMap<>();

        for (Home home : homes.all()) {
            byWorld.computeIfAbsent(home.world(), key -> new ArrayList<>()).add(home);
        }

        for (List<Home> group : byWorld.values()) {
            group.sort(Comparator.comparing(Home::name));
        }

        return byWorld;
    }
}
