package dev.kwlew.haven.home;

import dev.kwlew.haven.kernel.LifecycleComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the in-memory view of every online player's homes.
 * <p>
 * Threading contract:
 * <ul>
 *   <li>{@link #preload} runs <em>off</em> the main thread during pre-login and only ever touches
 *       {@link #pending}, which is concurrent. The player object does not exist yet, so nothing
 *       can observe a half-built container.</li>
 *   <li>{@link #cache} and every {@link PlayerHomes} inside it are <strong>main-thread only</strong>.</li>
 *   <li>Mutations happen on the main thread, then {@link #persist} hands an immutable snapshot to
 *       the storage thread. Write-through, so there is no dirty set and no data-loss window.</li>
 * </ul>
 */
public class HomeManager implements LifecycleComponent {

    /** How long a parked pre-login result may sit unclaimed before the sweeper drops it. */
    private static final long PENDING_MAX_AGE_MILLIS = 120_000L;
    private static final long SWEEP_INTERVAL_TICKS = 20L * 60L;

    private final JavaPlugin plugin;
    private final HomeStorage storage;

    private final Map<UUID, PlayerHomes> cache = new HashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private BukkitTask sweeper;

    public HomeManager(JavaPlugin plugin, HomeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public void start() {
        // Players already connected when the plugin enables (a /reload, or a late enable) never
        // fire AsyncPlayerPreLoginEvent, so they would otherwise never be loaded.
        for (Player player : Bukkit.getOnlinePlayers()) {
            install(player);
        }

        sweeper = Bukkit.getScheduler().runTaskTimer(
                plugin, this::sweepPending, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }

    @Override
    public void shutdown() {
        if (sweeper != null) {
            sweeper.cancel();
            sweeper = null;
        }

        // Queue a final write for everyone still online; HomeStorage#shutdown drains the queue.
        for (PlayerHomes homes : cache.values()) {
            storage.save(homes.snapshot());
        }

        cache.clear();
        pending.clear();
    }

    /**
     * Reads a player's file on the pre-login thread and parks the result until they join.
     * Blocking here delays only that one connection.
     */
    public void preload(UUID owner, String name) {
        pending.put(owner, new Pending(storage.load(owner, name), System.currentTimeMillis()));
    }

    /**
     * Drops a parked result for a connection that was denied after pre-login.
     */
    public void discardPending(UUID owner) {
        pending.remove(owner);
    }

    /**
     * Moves a parked result into the live cache. Falls back to a synchronous read for login paths
     * that skipped pre-login - one small file is sub-millisecond, and correctness beats purity.
     */
    public void install(Player player) {
        UUID owner = player.getUniqueId();
        Pending parked = pending.remove(owner);

        PlayerHomes.Snapshot snapshot = parked != null
                ? parked.snapshot()
                : storage.load(owner, player.getName());

        PlayerHomes homes = PlayerHomes.fromSnapshot(snapshot);
        homes.lastKnownName(player.getName());

        cache.put(owner, homes);
    }

    public void evict(Player player) {
        PlayerHomes homes = cache.remove(player.getUniqueId());

        if (homes != null) {
            storage.save(homes.snapshot());
        }
    }

    /**
     * The live container for an online player. Never returns null - a cache miss falls back to a
     * synchronous read so a command can't fail just because a login path was unusual.
     */
    public PlayerHomes homesOf(Player player) {
        UUID owner = player.getUniqueId();
        PlayerHomes homes = cache.get(owner);

        if (homes == null) {
            plugin.getLogger().warning("Homes for " + player.getName()
                    + " were not cached; loading synchronously.");

            homes = PlayerHomes.fromSnapshot(storage.load(owner, player.getName()));
            cache.put(owner, homes);
        }

        return homes;
    }

    /**
     * Home names for a player, or empty when they aren't cached. Unlike {@link #homesOf} this
     * never falls back to a disk read, so tab-completion can't block or spam the log.
     */
    public java.util.List<String> cachedNames(Player player) {
        PlayerHomes homes = cache.get(player.getUniqueId());

        return homes == null ? java.util.List.of() : homes.names();
    }

    /**
     * Total homes held by currently-cached players. Main thread only.
     */
    public int cachedHomeTotal() {
        int total = 0;

        for (PlayerHomes homes : cache.values()) {
            total += homes.count();
        }

        return total;
    }

    /**
     * Persists a container the caller has just mutated on the main thread.
     */
    public void persist(PlayerHomes homes) {
        storage.save(homes.snapshot());
    }

    private void sweepPending() {
        long cutoff = System.currentTimeMillis() - PENDING_MAX_AGE_MILLIS;

        pending.values().removeIf(parked -> parked.parkedAt() < cutoff);
    }

    private record Pending(PlayerHomes.Snapshot snapshot, long parkedAt) {}
}
