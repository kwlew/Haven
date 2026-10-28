package dev.kwlew.haven.command;

import dev.kwlew.haven.managers.home.Home;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.PlayerHomes;
import dev.kwlew.haven.managers.message.Messages;
import dev.kwlew.haven.managers.share.HomeSharing;
import dev.kwlew.haven.managers.sound.HavenSound;
import dev.kwlew.haven.managers.sound.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

/**
 * {@code /sharehome <home> <player>}; the recipient's hint is best-effort via {@link HomeSharing} and may fail without
 * undoing the share.
 */
public class ShareHomeCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final HomeSharing sharing;
    private final PlayerLookup lookup;
    private final Sounds sounds;

    public ShareHomeCommand(Messages messages,
                            HomeManager homeManager,
                            HomeSharing sharing,
                            PlayerLookup lookup,
                            Sounds sounds) {
        super(messages);
        this.homeManager = homeManager;
        this.sharing = sharing;
        this.lookup = lookup;
        this.sounds = sounds;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            return false;
        }
        run(sender, args[0], args.length == 2 ? args[1] : null);
        return true;
    }

    private void run(CommandSender sender, String rawHome, String playerName) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        if (!sharing.enabled()) {
            messages.send(player, "share.disabled");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        String name = Home.normalize(rawHome);
        PlayerHomes homes = homeManager.homesOf(player);
        Home home = homes.get(name);

        if (home == null) {
            messages.send(player, "home.not-found", Placeholder.unparsed("name", name));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (playerName == null) {
            list(player, home);
            return;
        }

        PlayerLookup.Target target = lookup.find(playerName);

        if (target == null) {
            messages.send(player, "share.player-not-found", Placeholder.unparsed("player", playerName));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (target.id().equals(player.getUniqueId())) {
            messages.send(player, "share.self");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (home.isSharedWith(target.id())) {
            messages.send(player, "share.already",
                    Placeholder.unparsed("name", home.name()),
                    Placeholder.unparsed("player", target.name()));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (home.sharedWith().size() >= sharing.maxPerHome()) {
            messages.send(player, "share.max-reached",
                    Placeholder.unparsed("max", Integer.toString(sharing.maxPerHome())),
                    Placeholder.unparsed("name", home.name()));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        homes.put(home.withSharedWith(target.id()));
        homeManager.persist(homes);
        sharing.addHint(target.id(), player.getUniqueId(), player.getName(), home.name());
        sounds.play(player, HavenSound.HOME_SET);

        messages.send(player, "share.shared",
                Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("player", target.name()));

        Player online = Bukkit.getPlayer(target.id());
        if (online != null) {
            notifyRecipient(online, player, home);
        }
    }

    /** The click is attached in code: MiniMessage does not expand placeholders inside a click tag's argument. */
    private void notifyRecipient(Player recipient, Player owner, Home home) {
        String command = "/home " + owner.getName() + ":" + home.name();

        messages.send(recipient, "share.received",
                Placeholder.unparsed("owner", owner.getName()),
                Placeholder.unparsed("name", home.name()),
                Placeholder.component("command",
                        Component.text(command).clickEvent(ClickEvent.runCommand(command))));
    }

    private void list(Player player, Home home) {
        if (home.sharedWith().isEmpty()) {
            messages.send(player, "share.list-empty", Placeholder.unparsed("name", home.name()));
            return;
        }

        String players = home.sharedWith().stream()
                .map(HomeSharing::nameOf)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));

        messages.send(player, "share.list",
                Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("players", players));
    }
}
