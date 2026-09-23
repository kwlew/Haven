package dev.kwlew.haven.listener;

import dev.kwlew.haven.command.OverwriteConfirmations;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.teleport.TeleportService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Loads a player's homes before they exist as a {@link org.bukkit.entity.Player}, installs them on
 * join, and flushes them on quit.
 * <p>
 * Reading during pre-login rather than on join removes the whole class of races that an async
 * load-on-join creates: there is no window in which a command could observe a half-loaded
 * container, because the player cannot run commands yet.
 */
public class ConnectionListener implements Listener, LifecycleComponent {

    private final JavaPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportService teleportService;
    private final OverwriteConfirmations confirmations;

    public ConnectionListener(JavaPlugin plugin,
                              HomeManager homeManager,
                              TeleportService teleportService,
                              OverwriteConfirmations confirmations) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.teleportService = teleportService;
        this.confirmations = confirmations;
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            // Another plugin rejected them; don't read a file nobody will claim.
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

    // There is deliberately no PlayerLoginEvent handler for connections denied *after* pre-login.
    // That event is deprecated in this API version and merely listening to it forces the Player
    // entity to be created early, in a half-initialised state - a real cost to every login, to
    // reclaim one small parked record a little sooner. HomeManager's sweeper handles it instead.

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
        // Cancel first: a warmup that fires after the player is gone would teleport nobody.
        teleportService.cancelSilently(event.getPlayer().getUniqueId());
        confirmations.clear(event.getPlayer().getUniqueId());
        homeManager.evict(event.getPlayer());
    }
}
