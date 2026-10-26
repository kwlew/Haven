package dev.kwlew.haven.command;

import dev.kwlew.haven.gui.HomesMenus;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.message.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /haven} root: opens the homes menu, or dispatches to a subcommand.
 * <p>
 * The root itself is open to everyone, because {@code help} is. Each subcommand is gated by its own
 * permission, checked here for the Bukkit path; the Brigadier adapter hides the literals a sender
 * may not use but routes through this class too, so a refusal is decided in one place.
 */
public class HavenCommand {

    private final Messages messages;
    private final HomesMenus menus;
    private final ReloadCommand reload;
    private final HelpCommand help;
    private final AdminCommand admin;
    private final HomeManager homeManager;
    private final PlayerLookup lookup;

    public HavenCommand(Messages messages, HomesMenus menus, ReloadCommand reload,
                        HelpCommand help, AdminCommand admin, HomeManager homeManager,
                        PlayerLookup lookup) {
        this.messages = messages;
        this.menus = menus;
        this.reload = reload;
        this.help = help;
        this.admin = admin;
        this.homeManager = homeManager;
        this.lookup = lookup;
    }

    /** The subcommands in the order they are offered, each with the permission that gates it. */
    private enum Sub {
        HELP("help", null),
        VERSION("version", AdminCommand.VERSION_PERMISSION),
        RELOAD("reload", ReloadCommand.PERMISSION),
        HOMES("homes", AdminCommand.HOMES_PERMISSION),
        TP("tp", AdminCommand.TP_PERMISSION),
        DELHOME("delhome", AdminCommand.DELHOME_PERMISSION);

        final String label;
        final String permission;

        Sub(String label, String permission) {
            this.label = label;
            this.permission = permission;
        }

        boolean permits(CommandSender sender) {
            return permission == null || sender.hasPermission(permission);
        }

        boolean takesPlayer() {
            return this == HOMES || this == TP || this == DELHOME;
        }

        static Sub of(String label) {
            for (Sub sub : values()) {
                if (sub.label.equalsIgnoreCase(label)) {
                    return sub;
                }
            }
            return null;
        }
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player && menus.available(player)) {
                menus.open(player);
            } else {
                // Sent here rather than returned as "false": Brigadier has no usage fallback.
                help.execute(sender);
            }
            return true;
        }

        Sub sub = Sub.of(args[0]);
        if (sub == null) {
            help.execute(sender);
            return true;
        }

        if (!sub.permits(sender)) {
            messages.send(sender, "general.no-permission");
            return true;
        }

        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        return switch (sub) {
            case HELP -> {
                if (rest.length != 0) {
                    yield false;
                }
                help.execute(sender);
                yield true;
            }
            case VERSION -> {
                if (rest.length != 0) {
                    yield false;
                }
                admin.version(sender);
                yield true;
            }
            case RELOAD -> args.length == 1 && reload.execute(sender, args);
            case HOMES -> admin.homes(sender, rest);
            case TP -> admin.tp(sender, rest);
            case DELHOME -> admin.delHome(sender, rest);
        };
    }

    /**
     * Bukkit-path completion: the subcommands the sender holds, then a player, then a home.
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            List<String> offered = new ArrayList<>();

            for (Sub sub : Sub.values()) {
                if (sub.permits(sender) && sub.label.startsWith(typed)) {
                    offered.add(sub.label);
                }
            }
            return offered;
        }

        Sub sub = Sub.of(args[0]);
        if (sub == null || !sub.permits(sender) || !sub.takesPlayer()) {
            return List.of();
        }

        if (args.length == 2) {
            return suggestPlayers(args[1]);
        }

        if (args.length == 3 && sub != Sub.HOMES) {
            return suggestHomes(args[1], args[2]);
        }

        return List.of();
    }

    /** Online player names for {@code <player>}; shared with the Brigadier adapter. */
    public List<String> suggestPlayers(String typed) {
        return lookup.onlineNames(typed);
    }

    /**
     * The target's home names, but only when they are online and so already in memory. Completion
     * can run off the main thread and must never read a file, so an offline target simply gets no
     * suggestions - the command itself still works if the name is typed out.
     */
    public List<String> suggestHomes(String playerName, String typed) {
        Player target = lookup.online(playerName);

        if (target == null) {
            return List.of();
        }

        String prefix = typed.toLowerCase(Locale.ROOT);
        return homeManager.cachedNames(target.getUniqueId()).stream()
                .filter(name -> name.startsWith(prefix))
                .toList();
    }
}
