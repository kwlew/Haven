package dev.kwlew.haven.teleport;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.kernel.LifecycleComponent;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Runs /home teleports: cooldown gate, cancellable warmup, then an async teleport.
 * <p>
 * The movement check lives in the warmup tick rather than a {@code PlayerMoveEvent} listener.
 * {@code PlayerMoveEvent} is the busiest event on the server and would be handled permanently for
 * a feature used a handful of times an hour - and it does not fire for vehicle movement, so a
 * player could drift across the map in a boat during a warmup. Sampling the player's block
 * position each tick costs nothing when no warmup is running and catches vehicles, velocity and
 * other plugins' teleports alike.
 */
public class TeleportService implements LifecycleComponent {

    private static final String BYPASS_WARMUP = "haven.bypass.warmup";
    private static final String BYPASS_COOLDOWN = "haven.bypass.cooldown";

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final Messages messages;
    private final HomeManager homeManager;
    private final Sounds sounds;

    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Map<UUID, Object> inFlight = new HashMap<>();

    public TeleportService(JavaPlugin plugin,
                           HavenConfig config,
                           Messages messages,
                           HomeManager homeManager,
                           Sounds sounds) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.homeManager = homeManager;
        this.sounds = sounds;
    }

    /**
     * Validates and begins a teleport. Every rejection path messages the player and returns.
     */
    public void teleport(Player player, Home home) {
        UUID id = player.getUniqueId();

        if (warmups.containsKey(id) || inFlight.containsKey(id)) {
            messages.send(player, "teleport.already-teleporting");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        if (player.isInsideVehicle()) {
            messages.send(player, "teleport.in-vehicle");
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        Location destination = home.toLocation(plugin.getServer());
        if (destination == null) {
            messages.send(player, "teleport.world-missing",
                    Placeholder.unparsed("name", home.name()),
                    Placeholder.unparsed("world", home.world()));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        long cooldown = cooldownRemainingSeconds(player);
        if (cooldown > 0) {
            messages.send(player, "teleport.cooldown",
                    Placeholder.unparsed("seconds", Long.toString(cooldown)));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        int warmupSeconds = player.hasPermission(BYPASS_WARMUP) ? 0 : config.warmupSeconds();

        if (warmupSeconds <= 0) {
            complete(player, home, destination);
            return;
        }

        messages.send(player, config.cancelOnMove()
                        ? "teleport.warmup-start" : "teleport.warmup-start-can-move",
                Placeholder.unparsed("name", home.name()),
                Placeholder.unparsed("seconds", Integer.toString(warmupSeconds)));

        // Warm the destination chunk while the player waits, so the teleport itself doesn't
        // stall on generation.
        destination.getWorld().getChunkAtAsync(destination);

        beginWarmup(player, home, destination, warmupSeconds);
    }

    private void beginWarmup(Player player, Home home, Location destination, int seconds) {
        Warmup warmup = new Warmup(home, destination, player.getLocation(), seconds * 20L);
        warmups.put(player.getUniqueId(), warmup);

        warmup.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player, warmup), 1L, 1L);
    }

    private void tick(Player player, Warmup warmup) {
        if (!player.isOnline()) {
            clear(player.getUniqueId());
            return;
        }

        if (config.cancelOnMove() && hasMoved(player, warmup.origin)) {
            cancel(player, "teleport.cancelled-move");
            return;
        }

        if (--warmup.remainingTicks > 0) {
            // One tick sound per second of remaining warmup, as an audible countdown.
            if (warmup.remainingTicks % 20 == 0) {
                sounds.play(player, HavenSound.WARMUP_TICK);
            }
            return;
        }

        clear(player.getUniqueId());
        complete(player, warmup.home, warmup.destination);
    }

    private boolean hasMoved(Player player, Location origin) {
        Location now = player.getLocation();

        return !now.getWorld().equals(origin.getWorld())
                || now.getBlockX() != origin.getBlockX()
                || now.getBlockY() != origin.getBlockY()
                || now.getBlockZ() != origin.getBlockZ();
    }

    private void complete(Player player, Home home, Location destination) {
        UUID id = player.getUniqueId();
        Object operation = new Object();
        inFlight.put(id, operation);

        try {
            player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .whenComplete((success, error) -> onMain(() -> {
                        // A quit can invalidate this operation; a later login may already have
                        // started another teleport under the same UUID.
                        if (!inFlight.remove(id, operation) || !player.isOnline()) {
                            return;
                        }
                        if (error != null) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Teleport to home '" + home.name() + "' failed for " + player.getName(), error);
                        }
                        if (error != null || !Boolean.TRUE.equals(success)) {
                            messages.send(player, "teleport.failed");
                            sounds.play(player, HavenSound.DENIED);
                            return;
                        }

                        markTeleported(player);
                        messages.send(player, "teleport.success",
                                Placeholder.unparsed("name", home.name()));
                        sounds.play(player, HavenSound.TELEPORT_SUCCESS);
                    }));
        } catch (RuntimeException error) {
            inFlight.remove(id, operation);
            plugin.getLogger().log(Level.WARNING,
                    "Could not start teleport to home '" + home.name() + "' for " + player.getName(), error);
            messages.send(player, "teleport.failed");
            sounds.play(player, HavenSound.DENIED);
        }
    }

    private void markTeleported(Player player) {
        PlayerHomes homes = homeManager.homesOf(player);
        homes.lastTeleport(System.currentTimeMillis());

        homeManager.persist(homes);
    }

    private long cooldownRemainingSeconds(Player player) {
        int cooldown = config.cooldownSeconds();

        if (cooldown <= 0 || player.hasPermission(BYPASS_COOLDOWN)) {
            return 0;
        }

        long last = homeManager.homesOf(player).lastTeleport();
        if (last <= 0) {
            return 0;
        }

        long remainingMillis = (cooldown * 1000L) - (System.currentTimeMillis() - last);

        return remainingMillis > 0 ? (long) Math.ceil(remainingMillis / 1000.0) : 0;
    }

    /**
     * Cancels a running warmup because the player took damage.
     */
    public void cancelForDamage(Player player) {
        if (config.cancelOnDamage() && warmups.containsKey(player.getUniqueId())) {
            cancel(player, "teleport.cancelled-damage");
        }
    }

    /**
     * Drops a warmup without messaging.
     */
    public void cancelSilently(UUID id) {
        clear(id);
        inFlight.remove(id);
    }

    private void cancel(Player player, String messageKey) {
        clear(player.getUniqueId());
        messages.send(player, messageKey);
        sounds.play(player, HavenSound.TELEPORT_CANCELLED);
    }

    private void clear(UUID id) {
        Warmup warmup = warmups.remove(id);

        if (warmup != null && warmup.task != null) {
            warmup.task.cancel();
        }
    }

    @Override
    public void shutdown() {
        for (Map.Entry<UUID, Warmup> entry : Map.copyOf(warmups).entrySet()) {
            Warmup warmup = entry.getValue();

            if (warmup.task != null) {
                warmup.task.cancel();
            }

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                messages.send(player, "teleport.cancelled-shutdown");
            }
        }

        warmups.clear();
        inFlight.clear();
    }

    /**
     * Runs on the main thread, immediately if already there.
     */
    private void onMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }

        // A teleport can still be in flight when the server stops.
        if (!plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, action);
    }

    private static final class Warmup {

        private final Home home;
        private final Location destination;
        private final Location origin;

        private long remainingTicks;
        private BukkitTask task;

        private Warmup(Home home, Location destination, Location origin, long remainingTicks) {
            this.home = home;
            this.destination = destination;
            this.origin = origin;
            this.remainingTicks = remainingTicks;
        }
    }
}
