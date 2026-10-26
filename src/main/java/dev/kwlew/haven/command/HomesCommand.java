package dev.kwlew.haven.command;

import dev.kwlew.haven.gui.HomesMenus;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeDisplay;
import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.home.WorldNames;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.share.HomeSharing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class HomesCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final HomeLimits limits;
    private final HomesMenus menus;
    private final HomeSharing sharing;

    public HomesCommand(Messages messages, HomeManager homeManager, HomeLimits limits,
                        HomesMenus menus, HomeSharing sharing) {
        super(messages);
        this.homeManager = homeManager;
        this.limits = limits;
        this.menus = menus;
        this.sharing = sharing;
    }

    /**
     * Bare {@code /homes} opens the menu when it is enabled and the player may use it, and falls
     * back to the chat list otherwise. {@code /homes list} is always the chat list, so the text
     * view stays reachable (and linkable) with the menu switched on.
     */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("list"))) {
            return false;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return true;
        }

        if (args.length == 0 && menus.available(player)) {
            menus.open(player);
        } else {
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
     * <p>
     * Homes other players shared with this one follow the worlds, on a line of their own. They are
     * not counted in the header: they don't use up the player's limit.
     */
    public void sendList(Player player) {
        PlayerHomes homes = homeManager.homesOf(player);
        List<PlayerHomes.Received> shared = sharing.visibleHints(homes);

        Runnable header = () -> messages.send(player, "home.list-header",
                Placeholder.unparsed("count", Integer.toString(homes.count())),
                Placeholder.unparsed("max", limits.describe(limits.limitFor(player))));
        Runnable footer = () -> messages.send(player, "home.list-footer");
        Runnable sharedLine = () -> sendShared(player, shared);

        sendList(player, homes, new Frame(
                header,
                () -> {
                    // Someone with only guest access still has something to show.
                    if (shared.isEmpty()) {
                        messages.send(player, "home.list-empty");
                        return;
                    }
                    header.run();
                    sharedLine.run();
                    footer.run();
                },
                footer,
                home -> "/home " + home.name(),
                sharedLine));
    }

    /**
     * One line of {@code owner:home} chips for the homes shared with the viewer. Built from hints, so
     * a chip can lead to a home that has since been unshared; running it says so and prunes the hint.
     */
    private void sendShared(Player viewer, List<PlayerHomes.Received> shared) {
        if (shared.isEmpty()) {
            return;
        }

        Component separator = renderOrEmpty("home.list-separator");
        List<Component> chips = shared.stream().map(this::sharedChip).toList();

        messages.send(viewer, "home.list-shared",
                Placeholder.unparsed("count", Integer.toString(shared.size())),
                Placeholder.component("homes", Component.join(JoinConfiguration.separator(separator), chips)));
    }

    /** As {@link #chip}, the click attached in code. The owner name is a valid player name, so safe to concatenate. */
    private Component sharedChip(PlayerHomes.Received hint) {
        return renderOrEmpty("home.list-shared-entry",
                Placeholder.unparsed("owner", hint.ownerName()),
                Placeholder.unparsed("name", hint.home()))
                .clickEvent(ClickEvent.runCommand("/home " + hint.ownerName() + ":" + hint.home()));
    }

    /**
     * The wording and click target around a list. {@code /haven homes <player>} renders the very
     * same body as {@code /homes}, so only these few things differ between the two.
     *
     * @param header  sent before the first world line
     * @param empty   sent instead of everything else when there are no homes
     * @param footer  sent after the last world line
     * @param command builds the command a chip runs; see {@link #chip} for why it is built in code
     * @param afterWorlds sent after the last world line and before the footer
     */
    public record Frame(Runnable header, Runnable empty, Runnable footer,
                        Function<Home, String> command, Runnable afterWorlds) {

        public Frame(Runnable header, Runnable empty, Runnable footer, Function<Home, String> command) {
            this(header, empty, footer, command, () -> {});
        }
    }

    /**
     * Renders {@code homes} to {@code viewer}, which need not be their owner.
     */
    public void sendList(CommandSender viewer, PlayerHomes homes, Frame frame) {
        if (homes.count() == 0) {
            frame.empty().run();
            return;
        }

        frame.header().run();

        Component separator = renderOrEmpty("home.list-separator");

        for (Map.Entry<String, List<Home>> group : WorldNames.groupByWorld(homes).entrySet()) {
            List<Component> chips = group.getValue().stream()
                    .map(home -> chip(home, frame.command()))
                    .toList();

            messages.send(viewer, worldKey(Bukkit.getWorld(group.getKey())),
                    Placeholder.unparsed("world", WorldNames.displayName(messages, group.getKey())),
                    Placeholder.unparsed("count", Integer.toString(group.getValue().size())),
                    Placeholder.component("homes",
                            Component.join(JoinConfiguration.separator(separator), chips)));
        }

        frame.afterWorlds().run();
        frame.footer().run();
    }

    /**
     * Picks the world-line message by dimension so the Nether and End read differently at a glance.
     * A {@code null} world is one that is not loaded - an orphaned home - and gets its own line so it
     * is visibly unusable rather than looking like any other world.
     */
    private String worldKey(World world) {
        if (world == null) {
            return "home.list-world-missing";
        }

        return switch (world.getEnvironment()) {
            case NETHER -> "home.list-world-nether";
            case THE_END -> "home.list-world-end";
            default -> "home.list-world";
        };
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
    private Component chip(Home home, Function<Home, String> command) {
        return renderOrEmpty("home.list-entry",
                HomeDisplay.placeholders(home, WorldNames.displayName(messages, home.world())))
                .clickEvent(ClickEvent.runCommand(command.apply(home)));
    }

    /**
     * {@link Messages#render} returns null for a message an admin has blanked out; inside a
     * composed line that has to become empty rather than propagate.
     */
    private Component renderOrEmpty(String key,
                                    TagResolver... resolvers) {
        Component rendered = messages.render(key, resolvers);

        return rendered == null ? Component.empty() : rendered;
    }
}
