package dev.kwlew.haven.command;

import dev.kwlew.haven.VersionSupport;
import dev.kwlew.haven.kernel.LifecycleComponent;
import org.bukkit.plugin.java.JavaPlugin;

/** Selects the command registrar supported by the running Paper server. */
public class HavenCommands implements LifecycleComponent {

    private final JavaPlugin plugin;
    private final SetHomeCommand setHome;
    private final HomeCommand home;
    private final DelHomeCommand delHome;
    private final HomesCommand homes;
    private final ReloadCommand reload;
    private final HomeSuggestions suggestions;
    private BukkitCommands bukkit;

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
        if (VersionSupport.supportsBrigadier(plugin.getServer().getBukkitVersion())
                && hasBrigadierApi()) {
            try {
                // The Java 21 adapter is loaded only after confirming the running API supports it.
                Class.forName("dev.kwlew.haven.command.BrigadierCommands", true,
                                plugin.getClass().getClassLoader())
                        .asSubclass(CommandRegistrar.class)
                        .getDeclaredConstructor().newInstance()
                        .register(plugin, setHome, home, delHome, homes, reload, suggestions);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not register Brigadier commands", e);
            }
        } else {
            bukkit = new BukkitCommands(plugin, setHome, home, delHome, homes, reload, suggestions);
            bukkit.register();
        }
    }

    @Override
    public void shutdown() {
        if (bukkit != null) {
            bukkit.unregister();
        }
    }

    private boolean hasBrigadierApi() {
        try {
            Class.forName("io.papermc.paper.command.brigadier.Commands", false,
                    plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
