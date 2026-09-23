package dev.kwlew.haven.api.papi;

import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.kernel.LifecycleComponent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Registers {@link PlaceholderAPI} when PlaceholderAPI is installed.
 * <p>
 * The expansion is held so it can be unregistered on shutdown. It declares
 * {@code persist() == true}, meaning PAPI keeps it across its own reloads - so without an explicit
 * unregister a disabled Haven would leave an expansion behind pointing at dead services.
 */
public class PlaceholderAPIHook implements LifecycleComponent {

    private static final String PLACEHOLDER_API = "PlaceholderAPI";

    private final JavaPlugin plugin;
    private final HomeManager homeManager;
    private final HomeLimits limits;

    private PlaceholderAPI expansion;

    public PlaceholderAPIHook(JavaPlugin plugin, HomeManager homeManager, HomeLimits limits) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.limits = limits;
    }

    @Override
    public void start() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(PLACEHOLDER_API)) {
            plugin.getLogger().info(PLACEHOLDER_API + " was not found.");
            return;
        }

        PlaceholderAPI candidate = new PlaceholderAPI(plugin, homeManager, limits);

        if (!candidate.register()) {
            plugin.getLogger().warning("Could not register the " + PLACEHOLDER_API + " expansion.");
            return;
        }

        expansion = candidate;

        plugin.getLogger().info(PLACEHOLDER_API + " hooked.");
    }

    @Override
    public void shutdown() {
        if (expansion == null) {
            return;
        }

        try {
            expansion.unregister();
        } catch (RuntimeException e) {
            // PAPI may already be gone if it was disabled before us; not worth failing shutdown.
            plugin.getLogger().log(Level.FINE, "Could not unregister the " + PLACEHOLDER_API + " expansion", e);
        } finally {
            expansion = null;
        }
    }
}
