package dev.kwlew.haven.kernel;

import dev.kwlew.haven.api.bStats;
import dev.kwlew.haven.api.papi.PlaceholderAPIHook;
import dev.kwlew.haven.command.HavenCommands;
import dev.kwlew.haven.listener.ConnectionListener;
import dev.kwlew.haven.listener.WarmupListener;
import dev.kwlew.haven.managers.config.FileMigrator;
import dev.kwlew.haven.managers.config.HavenConfig;
import dev.kwlew.haven.managers.config.MigrationNotice;
import dev.kwlew.haven.managers.gui.HomesMenus;
import dev.kwlew.haven.managers.home.HomeLimits;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.HomeStorage;
import dev.kwlew.haven.managers.home.YamlHomeStorage;
import dev.kwlew.haven.managers.message.Messages;
import dev.kwlew.haven.managers.share.HomeSharing;
import dev.kwlew.haven.managers.sound.Sounds;
import dev.kwlew.haven.managers.teleport.TeleportService;
import dev.kwlew.haven.managers.update.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

/**
 * Wires components together via {@link Registry} (depth-first, so dependencies are built first) and drives the
 * lifecycle.
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
        // Before HavenConfig and Messages: they read their files in the constructor, so an upgrade must land first.
        registry.resolve(FileMigrator.class);

        // Before anything that needs the HomeStorage interface, which the Registry can't construct on its own.
        registry.resolve(HavenConfig.class);
        registry.bind(HomeStorage.class, YamlHomeStorage.class);

        registry.resolve(Messages.class);
        registry.resolve(Sounds.class);
        registry.resolve(HomeLimits.class);
        registry.resolve(HomeManager.class);
        registry.resolve(HomeSharing.class);
        registry.resolve(UpdateChecker.class);
    }

    private void initGameplay() {
        registry.resolve(TeleportService.class);
        registry.resolve(HomesMenus.class);
    }

    private void initCommands() {
        registry.resolve(HavenCommands.class);
    }

    private void initListeners() {
        registry.resolve(ConnectionListener.class);
        registry.resolve(WarmupListener.class);
        registry.resolve(MigrationNotice.class);
    }

    /** Last on purpose: they report on core services, and so shut down first. */
    private void initAPI() {
        registry.resolve(bStats.class);
        registry.resolve(PlaceholderAPIHook.class);
    }

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
     * Shuts down every constructed component in reverse creation order, even if never {@code init()}ed (a
     * constructor may already own a resource, e.g. the non-daemon I/O thread). Safe to call twice.
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
