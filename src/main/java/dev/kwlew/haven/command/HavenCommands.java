package dev.kwlew.haven.command;

import dev.kwlew.haven.kernel.LifecycleComponent;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/** Registers commands declared in plugin.yml using the API available since Paper 1.18.2. */
public class HavenCommands implements LifecycleComponent {

    private final JavaPlugin plugin;
    private final SetHomeCommand setHome;
    private final HomeCommand home;
    private final DelHomeCommand delHome;
    private final HomesCommand homes;
    private final ReloadCommand reload;
    private final HomeSuggestions suggestions;

    public HavenCommands(JavaPlugin plugin, SetHomeCommand setHome, HomeCommand home,
                         DelHomeCommand delHome, HomesCommand homes, ReloadCommand reload,
                         HomeSuggestions suggestions) {
        this.plugin = plugin;
        this.setHome = setHome;
        this.home = home;
        this.delHome = delHome;
        this.homes = homes;
        this.reload = reload;
        this.suggestions = suggestions;
    }

    @Override
    public void start() {
        register("sethome", (sender, command, label, args) -> setHome.execute(sender, args));
        register("home", (sender, command, label, args) -> home.execute(sender, args));
        register("delhome", (sender, command, label, args) -> delHome.execute(sender, args));
        register("homes", (sender, command, label, args) -> homes.execute(sender, args));
        register("haven", (sender, command, label, args) -> reload.execute(sender, args));
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Missing command in plugin.yml: " + name);
        }
        command.setExecutor(executor);
        command.setTabCompleter(switch (name) {
            case "home", "delhome" -> suggestions;
            case "haven" -> (sender, cmd, alias, args) ->
                    sender.hasPermission("haven.admin.reload") && args.length == 1
                            && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))
                            ? List.of("reload") : List.of();
            default -> (sender, cmd, alias, args) -> List.of();
        });
    }
}
