package dev.kwlew.haven.command;

import dev.kwlew.haven.managers.gui.HomesMenus;
import dev.kwlew.haven.managers.home.Home;
import dev.kwlew.haven.managers.home.HomeDisplay;
import dev.kwlew.haven.managers.home.HomeLimits;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.PlayerHomes;
import dev.kwlew.haven.managers.home.WorldNames;
import dev.kwlew.haven.managers.message.Messages;
import dev.kwlew.haven.managers.share.HomeSharing;
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

    /** Bare {@code /homes} opens the menu if available; {@code /homes list} is always the chat list. */
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

    /** Also used by {@code /home} for an ambiguous no-argument teleport. Shared homes are not counted in the header. */
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
                    // Guest-only access still has something to show.
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

    /** Like {@link #chip}; the owner name is a valid player name, so concatenating it is safe. */
    private Component sharedChip(PlayerHomes.Received hint) {
        return renderOrEmpty("home.list-shared-entry",
                Placeholder.unparsed("owner", hint.ownerName()),
                Placeholder.unparsed("name", hint.home()))
                .clickEvent(ClickEvent.runCommand("/home " + hint.ownerName() + ":" + hint.home()));
    }

    /** Wording and click target around a list; shared with {@code /haven homes <player>}. */
    public record Frame(Runnable header, Runnable empty, Runnable footer,
                        Function<Home, String> command, Runnable afterWorlds) {

        public Frame(Runnable header, Runnable empty, Runnable footer, Function<Home, String> command) {
            this(header, empty, footer, command, () -> {});
        }
    }

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

    /** The click is attached in code because MiniMessage does not expand placeholders inside a click tag's argument. */
    private Component chip(Home home, Function<Home, String> command) {
        return renderOrEmpty("home.list-entry",
                HomeDisplay.placeholders(home, WorldNames.displayName(messages, home.world())))
                .clickEvent(ClickEvent.runCommand(command.apply(home)));
    }

    /**
     * {@link Messages#render} returns null for a blanked-out message; inside a composed line that must become empty.
     */
    private Component renderOrEmpty(String key,
                                    TagResolver... resolvers) {
        Component rendered = messages.render(key, resolvers);

        return rendered == null ? Component.empty() : rendered;
    }
}
