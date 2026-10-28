package dev.kwlew.haven.command;

import dev.kwlew.haven.managers.home.Home;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.PlayerHomes;
import dev.kwlew.haven.managers.message.Messages;
import dev.kwlew.haven.managers.share.HomeSharing;
import dev.kwlew.haven.managers.teleport.TeleportService;
import dev.kwlew.haven.managers.update.UpdateChecker;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * The {@code /haven} subcommands acting on other players' homes (possibly offline); continuations run on the main
 * thread.
 */
public class AdminCommand extends PlayerCommand {

    public static final String HOMES_PERMISSION = "haven.admin.homes";
    public static final String TP_PERMISSION = "haven.admin.tp";
    public static final String DELHOME_PERMISSION = "haven.admin.delhome";
    public static final String VERSION_PERMISSION = "haven.admin.version";

    private final JavaPlugin plugin;
    private final HomeManager homeManager;
    private final PlayerLookup lookup;
    private final TeleportService teleports;
    private final HomeSharing sharing;
    private final OverwriteConfirmations confirmations;
    private final HomesCommand homesCommand;
    private final UpdateChecker updates;

    public AdminCommand(JavaPlugin plugin, Messages messages, HomeManager homeManager,
                        PlayerLookup lookup, TeleportService teleports, HomeSharing sharing,
                        OverwriteConfirmations confirmations, HomesCommand homesCommand,
                        UpdateChecker updates) {
        super(messages);
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.lookup = lookup;
        this.teleports = teleports;
        this.sharing = sharing;
        this.confirmations = confirmations;
        this.homesCommand = homesCommand;
        this.updates = updates;
    }

    public void version(CommandSender sender) {
        messages.send(sender, "admin.version",
                Placeholder.unparsed("version", plugin.getDescription().getVersion()),
                Placeholder.unparsed("server", plugin.getServer().getVersion()));
        updates.sendStatus(sender);
    }

    public boolean homes(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return false;
        }

        withHomes(sender, args[0], (target, homes) -> homesCommand.sendList(sender, homes,
                new HomesCommand.Frame(
                        () -> messages.send(sender, "admin.homes-header",
                                Placeholder.unparsed("player", target.name()),
                                Placeholder.unparsed("count", Integer.toString(homes.count()))),
                        () -> messages.send(sender, "admin.homes-empty",
                                Placeholder.unparsed("player", target.name())),
                        () -> {},
                        home -> "/haven tp " + target.name() + " " + home.name())));
        return true;
    }

    public boolean tp(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }

        Player admin = requirePlayer(sender);
        if (admin == null) {
            return true;
        }

        withHomes(sender, args[0], (target, homes) -> {
            // The load may have taken a tick or two.
            if (!admin.isOnline()) {
                return;
            }

            Home home = homes.get(args[1]);
            if (home == null) {
                notFound(sender, target, args[1]);
                return;
            }

            teleports.teleportDirect(admin, home, target.name());
        });
        return true;
    }

    public boolean delHome(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }

        withHomes(sender, args[0], (target, homes) -> {
            Home removed = homes.remove(args[1]);
            if (removed == null) {
                notFound(sender, target, args[1]);
                return;
            }

            // A pending overwrite for a deleted home would be misleading.
            confirmations.clear(target.id());
            homeManager.persistAny(homes);
            // Recipients' hints would otherwise list a gone home; best effort.
            sharing.dropHints(target.id(), removed);

            // Echo coordinates so an accidental deletion can be recreated from chat history.
            messages.send(sender, "admin.deleted",
                    Placeholder.unparsed("player", target.name()),
                    Placeholder.unparsed("name", removed.name()),
                    Placeholder.unparsed("world", removed.world()),
                    Placeholder.unparsed("x", Integer.toString((int) Math.floor(removed.x()))),
                    Placeholder.unparsed("y", Integer.toString((int) Math.floor(removed.y()))),
                    Placeholder.unparsed("z", Integer.toString((int) Math.floor(removed.z()))));
        });
        return true;
    }

    private void notFound(CommandSender sender, PlayerLookup.Target target, String rawName) {
        messages.send(sender, "admin.home-not-found",
                Placeholder.unparsed("player", target.name()),
                Placeholder.unparsed("name", Home.normalize(rawName)));
    }

    private void withHomes(CommandSender sender, String playerName,
                           WithHomes action) {
        PlayerLookup.Target target = lookup.find(playerName);

        if (target == null) {
            messages.send(sender, "admin.player-not-found", Placeholder.unparsed("player", playerName));
            return;
        }

        homeManager.load(target.id()).whenComplete((homes, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not load homes for " + target.id(), error);
                messages.send(sender, "admin.load-failed", Placeholder.unparsed("player", target.name()));
                return;
            }

            try {
                action.accept(target, homes);
            } catch (RuntimeException e) {
                // whenComplete would otherwise swallow this.
                plugin.getLogger().log(Level.SEVERE, "Admin command failed for " + target.id(), e);
            }
        });
    }

    @FunctionalInterface
    private interface WithHomes {
        void accept(PlayerLookup.Target target, PlayerHomes homes);
    }
}
