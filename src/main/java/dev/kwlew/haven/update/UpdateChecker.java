package dev.kwlew.haven.update;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Only HTTP and JSON parsing run off-thread; all state and notifications stay on the server thread. */
public class UpdateChecker implements LifecycleComponent, Listener {

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final Messages messages;
    private final ModrinthClient client = new ModrinthClient();
    private final String installed;
    private final String minecraft;
    private ExecutorService worker;
    private Future<Optional<ModrinthClient.Release>> pending;
    private BukkitTask task;
    private ModrinthClient.Release available;
    private String announcedVersion;
    private long nextCheck;
    private boolean failureReported;

    public UpdateChecker(JavaPlugin plugin, HavenConfig config, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        installed = plugin.getDescription().getVersion();
        minecraft = plugin.getServer().getMinecraftVersion();
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        stopChecking();
        if (!config.updateCheckerEnabled()) return;
        if (ReleaseVersion.parse(installed).isEmpty()) {
            plugin.getLogger().warning("Update checker skipped: unrecognized plugin version '" + installed + "'.");
            return;
        }
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(action -> {
                Thread thread = new Thread(action, "Haven-update-checker");
                thread.setDaemon(true);
                return thread;
            });
        }
        nextCheck = System.nanoTime();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
    }

    private void tick() {
        if (pending != null && pending.isDone()) {
            try {
                available = pending.get().orElse(null);
                failureReported = false;
                if (available != null && !available.version().equals(announcedVersion)) {
                    announcedVersion = available.version();
                    plugin.getLogger().info("Update available: Haven " + available.version()
                            + " (installed: " + installed + "). " + available.url());
                    plugin.getServer().getOnlinePlayers().forEach(this::notifyPlayer);
                }
            } catch (ExecutionException e) {
                reportFailure(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pending = null;
            }
        }
        if (pending == null && System.nanoTime() - nextCheck >= 0) {
            nextCheck = System.nanoTime() + TimeUnit.HOURS.toNanos(config.updateCheckIntervalHours());
            pending = worker.submit(() -> client.check(installed, minecraft));
        }
    }

    private void reportFailure(Throwable error) {
        // Report one warning per outage, and retry at the normal interval.
        if (!failureReported) {
            plugin.getLogger().warning("Could not check Modrinth for updates: " + error.getMessage()
                    + ". Will retry at the next scheduled check.");
            failureReported = true;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        notifyPlayer(event.getPlayer());
    }

    private void notifyPlayer(Player player) {
        if (available == null || !config.updateNotifyAdmins()
                || !player.hasPermission("haven.admin.update")) return;
        messages.send(player, "admin.update-available",
                Placeholder.unparsed("current", installed),
                Placeholder.unparsed("latest", available.version()),
                Placeholder.component("link", Component.text(available.url())
                        .clickEvent(ClickEvent.openUrl(available.url()))));
    }

    private void stopChecking() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        available = null;
    }

    @Override
    public void shutdown() {
        stopChecking();
        HandlerList.unregisterAll(this);
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
    }
}
