package dev.kwlew.haven.listener;

import dev.kwlew.haven.command.OverwriteConfirmations;
import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.managers.home.HomeLimits;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.teleport.TeleportService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/** Loads homes during pre-login, before the player can run commands, so nothing observes a half-loaded container. */
public class ConnectionListener implements Listener, LifecycleComponent {

    private final JavaPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportService teleportService;
    private final OverwriteConfirmations confirmations;
    private final HomeLimits limits;

    public ConnectionListener(JavaPlugin plugin,
                              HomeManager homeManager,
                              TeleportService teleportService,
                              OverwriteConfirmations confirmations,
                              HomeLimits limits) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.teleportService = teleportService;
        this.confirmations = confirmations;
        this.limits = limits;
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            // Another plugin rejected them.
            homeManager.discardPending(event.getUniqueId());
            return;
        }

        try {
            homeManager.preload(event.getUniqueId(), event.getName());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load homes for " + event.getUniqueId(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "Haven could not safely load your homes. Please contact a server administrator.");
        }
    }

    // No PlayerLoginEvent handler for post-pre-login denials: listening forces early Player creation on every login.
    // HomeManager's sweeper reclaims the parked record instead.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        try {
            homeManager.install(event.getPlayer());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not install homes for " + event.getPlayer().getUniqueId(), e);
            event.getPlayer().kickPlayer("Haven could not safely load your homes. Please contact a server administrator.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        teleportService.cancelSilently(event.getPlayer().getUniqueId());
        confirmations.clear(event.getPlayer().getUniqueId());
        limits.invalidate(event.getPlayer().getUniqueId());
        homeManager.evict(event.getPlayer());
    }
}
