package dev.kwlew.haven.home;

import dev.kwlew.haven.message.Messages;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * How homes are grouped and how their worlds are named for display.
 * <p>
 * Shared by the chat list and the menu so the two never disagree about order or spelling.
 */
public final class WorldNames {

    private WorldNames() {}

    /**
     * The name shown to the player for a world folder name.
     * <p>
     * Resolution order: an admin override in {@code world-names}, then the vanilla dimension names
     * for the main world and its {@code _nether} / {@code _the_end} companions, then a prettified
     * form of the folder name. The dimension names are only applied when the main world is
     * loaded, since without it there is nothing to anchor them to.
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

    /**
     * {@code resource_world} becomes {@code Resource World}.
     */
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

    /**
     * Worlds sorted by name, and homes sorted by name within each - so the list stays in the same
     * order between calls rather than shifting as homes are added and removed.
     */
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
