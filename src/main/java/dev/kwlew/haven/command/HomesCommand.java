package dev.kwlew.haven.command;

import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HomesCommand extends PlayerCommand {

    private static final DateTimeFormatter CREATED_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    private final HomeManager homeManager;
    private final HomeLimits limits;

    public HomesCommand(Messages messages, HomeManager homeManager, HomeLimits limits) {
        super(messages);
        this.homeManager = homeManager;
        this.limits = limits;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length != 0) {
            return false;
        }
        Player player = requirePlayer(sender);
        if (player != null) {
            sendList(player);
        }
        return true;
    }

    /**
     * Renders the home list, grouped by world with each name as a clickable chip.
     * <p>
     * Grouping keeps the output to one line per world instead of one per home, which matters for
     * a player near a high limit - a flat list of twenty homes would push the rest of chat away.
     * <p>
     * Shared with {@code /home} so an ambiguous no-argument teleport shows the same thing.
     */
    public void sendList(Player player) {
        PlayerHomes homes = homeManager.homesOf(player);

        if (homes.count() == 0) {
            messages.send(player, "home.list-empty");
            return;
        }

        messages.send(player, "home.list-header",
                Placeholder.unparsed("count", Integer.toString(homes.count())),
                Placeholder.unparsed("max", limits.describe(limits.limitFor(player))));

        Component separator = renderOrEmpty("home.list-separator");

        for (Map.Entry<String, List<Home>> group : groupByWorld(homes).entrySet()) {
            List<Component> chips = group.getValue().stream()
                    .map(this::chip)
                    .toList();

            messages.send(player, "home.list-world",
                    Placeholder.unparsed("world", group.getKey()),
                    Placeholder.unparsed("count", Integer.toString(group.getValue().size())),
                    Placeholder.component("homes",
                            Component.join(JoinConfiguration.separator(separator), chips)));
        }
    }

    /**
     * Worlds sorted by name, and homes sorted by name within each - so the list stays in the same
     * order between calls rather than shifting as homes are added and removed.
     */
    private Map<String, List<Home>> groupByWorld(PlayerHomes homes) {
        Map<String, List<Home>> byWorld = new TreeMap<>();

        for (Home home : homes.all()) {
            byWorld.computeIfAbsent(home.world(), key -> new ArrayList<>()).add(home);
        }

        for (List<Home> group : byWorld.values()) {
            group.sort(Comparator.comparing(Home::name));
        }

        return byWorld;
    }

    /**
     * One clickable home name.
     * <p>
     * The click action is attached here rather than in {@code messages.yml} because MiniMessage
     * does not expand placeholders inside a click tag's argument - {@code '/home <name>'} would
     * survive into the command verbatim. Hover text has no such problem, so it stays configurable.
     * Home names are validated against {@code [a-z0-9_-]{1,16}}, so building the command by
     * concatenation is safe.
     */
    private Component chip(Home home) {
        return renderOrEmpty("home.list-entry",
                Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("world", home.world()),
                Placeholder.unparsed("x", Integer.toString((int) Math.floor(home.x()))),
                Placeholder.unparsed("y", Integer.toString((int) Math.floor(home.y()))),
                Placeholder.unparsed("z", Integer.toString((int) Math.floor(home.z()))),
                Placeholder.unparsed("created", created(home)))
                .clickEvent(ClickEvent.runCommand("/home " + home.name()));
    }

    private String created(Home home) {
        return home.created() <= 0
                ? "unknown"
                : CREATED_FORMAT.format(Instant.ofEpochMilli(home.created()));
    }

    /**
     * {@link Messages#render} returns null for a message an admin has blanked out; inside a
     * composed line that has to become empty rather than propagate.
     */
    private Component renderOrEmpty(String key,
                                    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        Component rendered = messages.render(key, resolvers);

        return rendered == null ? Component.empty() : rendered;
    }
}
