package dev.kwlew.haven.kernel;

import dev.kwlew.haven.api.bStats;
import dev.kwlew.haven.api.papi.PlaceholderAPIHook;
import dev.kwlew.haven.command.HavenCommands;
import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.HomeStorage;
import dev.kwlew.haven.home.YamlHomeStorage;
import dev.kwlew.haven.listener.ConnectionListener;
import dev.kwlew.haven.listener.WarmupListener;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.Sounds;
import dev.kwlew.haven.teleport.TeleportService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

/**
 * Wires every component together and drives the plugin's lifecycle.
 * <p>
 * Registration is grouped by feature area purely for readability - {@link Registry#resolve} is
 * depth-first, so a component's dependencies are always constructed before it regardless of which
 * group pulled it in. Group order only decides the relative position of components with no
 * dependency edge between them, which matters in exactly two places, both called out below.
 */
public class Bootstrap {

    private final Registry registry = new Registry();
    private final JavaPlugin plugin;

    private boolean tornDown;

    public Bootstrap(JavaPlugin plugin) {
        this.plugin = plugin;

        registry.register(JavaPlugin.class, plugin);
        registry.register(Registry.class, registry);
    }

    public void init() {
        initCore();
        initGameplay();
        initCommands();
        initListeners();
        initAPI();

        registry.seal();

        runLifecycle();
    }

    private void initCore() {
        // Must come first: anything depending on the HomeStorage interface would otherwise fail
        // to resolve, since the Registry can't construct an interface on its own.
        registry.resolve(HavenConfig.class);
        registry.bind(HomeStorage.class, YamlHomeStorage.class);

        registry.resolve(Messages.class);
        registry.resolve(Sounds.class);
        registry.resolve(HomeLimits.class);
        registry.resolve(HomeManager.class);
    }

    private void initGameplay() {
        registry.resolve(TeleportService.class);
    }

    private void initCommands() {
        registry.resolve(HavenCommands.class);
    }

    private void initListeners() {
        registry.resolve(ConnectionListener.class);
        registry.resolve(WarmupListener.class);
    }

    /**
     * Optional third-party hooks. Last on purpose: both report on core services, so those must
     * already exist. Being last also means they shut down first, before the data they read.
     */
    private void initAPI() {
        registry.resolve(bStats.class);
        registry.resolve(PlaceholderAPIHook.class);
    }

    /**
     * Runs {@code init()} across every component, then {@code start()}. A failure in either phase
     * tears down whatever was already brought up rather than leaving the plugin half-enabled.
     */
    private void runLifecycle() {
        List<LifecycleComponent> components = lifecycleComponents(registry.getAll());

        try {
            for (LifecycleComponent component : components) {
                component.init();
            }

            for (LifecycleComponent component : components) {
                component.start();
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Startup failed; rolling back components that were brought up.");
            shutdown();
            throw e;
        }
    }

    /**
     * Shuts down every component the {@link Registry} managed to construct, in reverse creation
     * order, so a component is always torn down before the dependencies it was built from.
     * <p>
     * Deliberately keyed off what was <em>constructed</em> rather than what was successfully
     * initialised. A constructor can already own a resource - {@code YamlHomeStorage} starts its
     * non-daemon I/O thread there - so if a later component fails to construct, that thread still
     * has to be stopped or it keeps the JVM alive and the server never finishes stopping. This is
     * why every {@code shutdown()} must tolerate never having been {@code init()}ed.
     * <p>
     * Safe to call twice: the rollback path and {@code onDisable} can both reach it.
     */
    public void shutdown() {
        if (tornDown) {
            return;
        }

        tornDown = true;

        for (LifecycleComponent component : lifecycleComponents(registry.getAllReversed())) {
            try {
                component.shutdown();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error shutting down " + component.getClass().getSimpleName(), e);
            }
        }
    }

    private List<LifecycleComponent> lifecycleComponents(List<Object> instances) {
        return instances.stream()
                .filter(LifecycleComponent.class::isInstance)
                .map(LifecycleComponent.class::cast)
                .toList();
    }

    public Registry registry() {
        return registry;
    }
}
