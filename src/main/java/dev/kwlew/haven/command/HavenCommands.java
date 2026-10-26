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
    private final RenameHomeCommand renameHome;
    private final ShareHomeCommand shareHome;
    private final UnshareHomeCommand unshareHome;
    private final HomesCommand homes;
    private final HavenCommand haven;
    private final HomeSuggestions suggestions;
    private BukkitCommands bukkit;

    public HavenCommands(JavaPlugin plugin, SetHomeCommand setHome, HomeCommand home,
                         DelHomeCommand delHome, RenameHomeCommand renameHome,
                         ShareHomeCommand shareHome, UnshareHomeCommand unshareHome,
                         HomesCommand homes, HavenCommand haven,
                         HomeSuggestions suggestions) {
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
                        .register(plugin, setHome, home, delHome, renameHome, shareHome, unshareHome,
                                homes, haven, suggestions);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not register Brigadier commands", e);
            }
        } else {
            bukkit = new BukkitCommands(plugin, setHome, home, delHome, renameHome, shareHome,
                    unshareHome, homes, haven, suggestions);
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
