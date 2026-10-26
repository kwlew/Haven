package dev.kwlew.haven.command;

import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.home.WorldNames;
import dev.kwlew.haven.home.WorldRules;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.share.SharedVisits;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import dev.kwlew.haven.teleport.TeleportService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomeCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final TeleportService teleportService;
    private final HomesCommand homesCommand;
    private final WorldRules worldRules;
    private final SharedVisits visits;
    private final Sounds sounds;

    public HomeCommand(Messages messages,
                       HomeManager homeManager,
                       TeleportService teleportService,
                       HomesCommand homesCommand,
                       WorldRules worldRules,
                       SharedVisits visits,
                       Sounds sounds) {
        super(messages);
        this.homeManager = homeManager;
        this.teleportService = teleportService;
        this.homesCommand = homesCommand;
        this.worldRules = worldRules;
        this.visits = visits;
        this.sounds = sounds;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 1) {
            return false;
        }
        run(sender, args.length == 0 ? null : args[0]);
        return true;
    }

    private void run(CommandSender sender, String rawName) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        // Before the empty-list check: someone with no homes of their own can still be a guest.
        // Home names can't contain ':', so this can never be one of the player's own homes.
        int separator = rawName == null ? -1 : rawName.indexOf(':');

        if (separator >= 0) {
            visits.visit(player, rawName.substring(0, separator), rawName.substring(separator + 1));
            return;
        }

        PlayerHomes homes = homeManager.homesOf(player);

        if (homes.count() == 0) {
            messages.send(player, "home.list-empty");
            return;
        }

        Home target;

        if (rawName != null) {
            target = homes.get(rawName);

            if (target == null) {
                messages.send(player, "home.not-found",
                        Placeholder.unparsed("name", Home.normalize(rawName)));
                sounds.play(player, HavenSound.DENIED);
                return;
            }
        } else {
            target = defaultHome(homes);

            if (target == null) {
                // Ambiguous - show the list rather than guessing which home they meant.
                homesCommand.sendList(player);
                return;
            }
        }

        if (!worldRules.teleportAllowed(player, target.world())) {
            messages.send(player, "teleport.world-disabled",
                    Placeholder.unparsed("name", target.name()),
                    Placeholder.unparsed("world", WorldNames.displayName(messages, target.world())));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        teleportService.teleport(player, target);
    }

    /**
     * Resolves bare {@code /home}. A home literally named "home" wins before the only-one-home
     * shortcut, so behaviour doesn't change for a player the moment they add a second home.
     */
    private Home defaultHome(PlayerHomes homes) {
        Home named = homes.get(SetHomeCommand.DEFAULT_HOME);
        if (named != null) {
            return named;
        }

        return homes.count() == 1 ? homes.all().iterator().next() : null;
    }
}
