package dev.kwlew.haven.command;

import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.share.HomeSharing;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * {@code /unsharehome <home> <player>}: the inverse of {@link ShareHomeCommand}.
 */
public class UnshareHomeCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final HomeSharing sharing;
    private final PlayerLookup lookup;
    private final Sounds sounds;

    public UnshareHomeCommand(Messages messages,
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
        if (args.length != 2) {
            return false;
        }
        run(sender, args[0], args[1]);
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

        UUID recipient = recipient(home, playerName);

        if (recipient == null) {
            messages.send(player, "share.not-shared-with",
                    Placeholder.unparsed("name", home.name()),
                    Placeholder.unparsed("player", playerName));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        homes.put(home.withoutSharedWith(recipient));
        homeManager.persist(homes);
        sharing.removeHint(recipient, player.getUniqueId(), home.name());
        sounds.play(player, HavenSound.HOME_DELETED);

        messages.send(player, "share.unshared",
                Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("player", HomeSharing.nameOf(recipient)));

        Player online = Bukkit.getPlayer(recipient);
        if (online != null) {
            messages.send(online, "share.revoked",
                    Placeholder.unparsed("owner", player.getName()),
                    Placeholder.unparsed("name", home.name()));
        }
    }

    /**
     * Which of the home's recipients the typed name means, or {@code null}. Matches against the
     * recipients themselves rather than asking the server who the name is: a player who has not
     * been seen for a while can drop out of the server's name cache, and that must not leave the
     * owner unable to revoke their access.
     */
    private UUID recipient(Home home, String typed) {
        PlayerLookup.Target known = lookup.find(typed);

        if (known != null && home.isSharedWith(known.id())) {
            return known.id();
        }

        for (UUID shared : home.sharedWith()) {
            if (HomeSharing.nameOf(shared).equalsIgnoreCase(typed)) {
                return shared;
            }
        }

        return null;
    }
}
