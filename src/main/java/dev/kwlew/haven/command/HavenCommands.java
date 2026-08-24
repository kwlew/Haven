package dev.kwlew.haven.command;

import dev.kwlew.haven.kernel.LifecycleComponent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Registers Haven's commands through Paper's Brigadier lifecycle API.
 * <p>
 * The handler is registered in {@link #start()} because that runs inside {@code onEnable}, the
 * only phase Paper accepts lifecycle handler registration in - doing it from an async callback or
 * from {@code /haven reload} throws.
 * <p>
 * Nodes are built fresh inside the callback on every invocation. The registrar event fires again
 * on {@code /minecraft:reload} and on datapack reloads, so caching or reusing nodes would
 * accumulate stale trees.
 */
public class HavenCommands implements LifecycleComponent {

    private final JavaPlugin plugin;
    private final SetHomeCommand setHome;
    private final HomeCommand home;
    private final DelHomeCommand delHome;
    private final HomesCommand homes;
    private final ReloadCommand reload;

    public HavenCommands(JavaPlugin plugin,
                         SetHomeCommand setHome,
                         HomeCommand home,
                         DelHomeCommand delHome,
                         HomesCommand homes,
                         ReloadCommand reload) {
        this.plugin = plugin;
        this.setHome = setHome;
        this.home = home;
        this.delHome = delHome;
        this.homes = homes;
        this.reload = reload;
    }

    @Override
    public void start() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();

            registrar.register(setHome.node(), "Save a home at your current location", List.of());
            registrar.register(home.node(), "Teleport to one of your homes", List.of());
            registrar.register(delHome.node(), "Delete one of your homes", List.of("removehome"));
            registrar.register(homes.node(), "List your homes", List.of("listhomes"));
            registrar.register(reload.node(), "Haven administration", List.of());
        });
    }
}
