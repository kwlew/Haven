package dev.kwlew.haven.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Programmatic fallback for servers older than Paper 1.20.6. */
final class BukkitCommands {

    private final JavaPlugin plugin;
    private final SetHomeCommand setHome;
    private final HomeCommand home;
    private final DelHomeCommand delHome;
    private final RenameHomeCommand renameHome;
    private final ShareHomeCommand shareHome;
    private final UnshareHomeCommand unshareHome;
    private final HomesCommand homes;
    private final HavenCommand haven;
    private final HomeSuggestions suggestions;
    private final List<Command> registered = new ArrayList<>();

    BukkitCommands(JavaPlugin plugin, SetHomeCommand setHome, HomeCommand home,
                   DelHomeCommand delHome, RenameHomeCommand renameHome,
                   ShareHomeCommand shareHome, UnshareHomeCommand unshareHome, HomesCommand homes,
                   HavenCommand haven, HomeSuggestions suggestions) {
        this.plugin = plugin;
        this.setHome = setHome;
        this.home = home;
        this.delHome = delHome;
        this.renameHome = renameHome;
        this.shareHome = shareHome;
        this.unshareHome = unshareHome;
        this.homes = homes;
        this.haven = haven;
        this.suggestions = suggestions;
    }

    void register() {
        add(CommandSpec.SET_HOME, (sender, cmd, label, args) -> setHome.execute(sender, args),
                (sender, cmd, label, args) -> List.of());
        add(CommandSpec.HOME, (sender, cmd, label, args) -> home.execute(sender, args),
                suggestions.forHome());
        add(CommandSpec.DEL_HOME, (sender, cmd, label, args) -> delHome.execute(sender, args), suggestions);
        add(CommandSpec.RENAME_HOME, (sender, cmd, label, args) -> renameHome.execute(sender, args),
                suggestions);
        add(CommandSpec.SHARE_HOME, (sender, cmd, label, args) -> shareHome.execute(sender, args),
                suggestions.forShare());
        add(CommandSpec.UNSHARE_HOME, (sender, cmd, label, args) -> unshareHome.execute(sender, args),
                suggestions.forUnshare());
        add(CommandSpec.HOMES, (sender, cmd, label, args) -> homes.execute(sender, args),
                (sender, cmd, label, args) ->
                        args.length == 1 && "list".startsWith(args[0].toLowerCase(Locale.ROOT))
                                ? List.of("list") : List.of());
        // No command-level permission: the root is open and each subcommand is enforced per node.
        add(CommandSpec.HAVEN, (sender, cmd, label, args) -> haven.execute(sender, args),
                (sender, cmd, label, args) -> haven.tabComplete(sender, args));
    }

    void unregister() {
        CommandMap map = plugin.getServer().getCommandMap();
        map.getKnownCommands().values().removeIf(registered::contains);
        for (Command command : registered) {
            command.unregister(map);
        }
        registered.clear();
    }

    private void add(CommandSpec spec, CommandExecutor executor, TabCompleter completer) {
        Command command = new Command(spec.name, spec.description, spec.usage, spec.aliases) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!testPermission(sender)) {
                    return true;
                }
                if (!executor.onCommand(sender, this, label, args)) {
                    sender.sendMessage(getUsage());
                }
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (!testPermissionSilent(sender)) {
                    return List.of();
                }
                return completer.onTabComplete(sender, this, alias, args);
            }
        };
        command.setPermission(spec.permission);
        plugin.getServer().getCommandMap().register(plugin.getName().toLowerCase(Locale.ROOT), command);
        registered.add(command);
    }
}
