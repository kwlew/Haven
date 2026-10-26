package dev.kwlew.haven.home;

import dev.kwlew.haven.kernel.LifecycleComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

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
 *   <li>{@link #load} / {@link #persistAny} reach players who are <em>not</em> online. Their
 *       containers are short-lived copies that never enter {@link #cache}; see
 *       {@link #persistAny} for how that stays consistent with a login.</li>
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

    /** Offline loads still being read, so concurrent requests for one player share one copy. Main thread only. */
    private final Map<UUID, CompletableFuture<PlayerHomes>> loading = new HashMap<>();

    /** Ticks up on every offline write; {@link Pending} remembers the value its read started at. */
    private final AtomicLong writeEpoch = new AtomicLong();
    private final Map<UUID, OfflineWrite> offlineWrites = new ConcurrentHashMap<>();

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
        loading.clear();
        offlineWrites.clear();
    }

    /**
     * Reads a player's file on the pre-login thread and parks the result until they join.
     * Blocking here delays only that one connection.
     */
    public void preload(UUID owner, String name) {
        // Sampled before the read is queued: an offline write stamped after this point may have
        // been queued behind our read, in which case the parked copy is stale (see persistAny).
        long readEpoch = writeEpoch.get();

        pending.put(owner, new Pending(storage.load(owner, name), System.currentTimeMillis(), readEpoch));
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
        OfflineWrite offlineWrite = offlineWrites.remove(owner);

        // An offline edit landed after this copy was read, so it predates that edit. Read again:
        // the new read queues behind the edit's write and sees it.
        boolean stale = parked != null && offlineWrite != null && parked.readEpoch() < offlineWrite.epoch();

        PlayerHomes.Snapshot snapshot = parked != null && !stale
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
        return cachedNames(player.getUniqueId());
    }

    /**
     * As {@link #cachedNames(Player)}, by id - for admin completion on another player's homes.
     */
    public java.util.List<String> cachedNames(UUID id) {
        PlayerHomes homes = cache.get(id);

        return homes == null ? java.util.List.of() : homes.names();
    }

    /**
     * The live container for a player, or {@code null} when they aren't cached. Like
     * {@link #cachedNames} it never falls back to a disk read, so tab-completion can use it to look
     * at homes and received-home hints without blocking. Treat the result as read-only.
     */
    public PlayerHomes cached(Player player) {
        return cache.get(player.getUniqueId());
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

    /**
     * Any player's homes, online or not. Completes <strong>on the main thread</strong>, never
     * blocks it, and fails the future if the file exists but cannot be read safely.
     * <p>
     * An online player (or one cached in the login window) yields the live cached instance
     * immediately. Otherwise the file is read on the storage thread. A player with no file at all -
     * never set a home, never joined - yields an empty container rather than an error, because
     * {@link HomeStorage#load} already treats a missing file that way.
     * <p>
     * If the player logged in while the read was in flight, the cached instance wins over the disk
     * copy: it is the one that will be saved on quit, so editing the older copy would be lost.
     * <p>
     * An offline result is a detached copy. Apply the change and call {@link #persistAny} in the
     * same continuation, so no tick passes in which the player could log in and diverge from it.
     */
    public CompletableFuture<PlayerHomes> load(UUID id) {
        PlayerHomes cached = cache.get(id);

        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Two commands about one offline player must edit the same copy. With separate copies the
        // second would be built from disk that does not yet contain the first one's write.
        CompletableFuture<PlayerHomes> inFlight = loading.get(id);

        if (inFlight != null) {
            return inFlight;
        }

        CompletableFuture<PlayerHomes> result = new CompletableFuture<>();
        loading.put(id, result);

        storage.loadAsync(id, null).whenComplete((snapshot, error) -> onMain(result, () -> {
            loading.remove(id, result);

            PlayerHomes now = cache.get(id);

            if (now != null) {
                result.complete(now);
            } else if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(PlayerHomes.fromSnapshot(snapshot));
            }
        }));

        return result;
    }

    /**
     * Persists a container obtained from {@link #load}, whether or not its owner is online. Main
     * thread only.
     * <p>
     * For the cached instance this is exactly {@link #persist}. For an offline copy it saves the
     * snapshot <em>and</em> invalidates any parked pre-login result for that player.
     * <p>
     * The race this prevents: a player's pre-login read is queued on the storage thread, then an
     * admin's write is queued behind it. The read finishes first, so the parked copy lacks the
     * edit; the join would install it, and the player's next save (on quit) would silently undo
     * the admin's change. Dropping the parked copy makes the join fall back to a fresh read, which
     * queues behind the write on the same FIFO thread and so sees it. A read that is <em>still in
     * flight</em> can park after we looked, so the write also leaves an epoch stamp that
     * {@link #install} checks against the epoch the parked read started at.
     * <p>
     * If the owner came online after the copy was loaded, the copy is stale and writing it could
     * clobber what they have done since; the write is refused and logged instead.
     */
    public void persistAny(PlayerHomes homes) {
        UUID owner = homes.owner();
        PlayerHomes live = cache.get(owner);

        if (live == homes) {
            persist(homes);
            return;
        }

        if (live != null) {
            plugin.getLogger().warning("Not saving an offline copy of " + owner
                    + "'s homes: they logged in since it was loaded.");
            return;
        }

        // Write first, then stamp: a pre-login read that samples the epoch after the stamp is
        // necessarily queued after this write, so it cannot be stale.
        storage.save(homes.snapshot());

        offlineWrites.put(owner, new OfflineWrite(writeEpoch.incrementAndGet(), System.currentTimeMillis()));
        pending.remove(owner);
    }

    /**
     * Completes {@code result} from the main thread, or fails it if the plugin is already
     * disabled and the scheduler would refuse the task.
     */
    private void onMain(CompletableFuture<?> result, Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }

        if (!plugin.isEnabled()) {
            result.completeExceptionally(new IllegalStateException("Haven is shutting down"));
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, action);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not hand a home load back to the main thread", e);
            result.completeExceptionally(e);
        }
    }

    private void sweepPending() {
        long cutoff = System.currentTimeMillis() - PENDING_MAX_AGE_MILLIS;

        pending.values().removeIf(parked -> parked.parkedAt() < cutoff);
        // A stamp only matters to a read that started before it, and reads are no older than this.
        offlineWrites.values().removeIf(write -> write.at() < cutoff);
    }

    private record Pending(PlayerHomes.Snapshot snapshot, long parkedAt, long readEpoch) {}

    private record OfflineWrite(long epoch, long at) {}
}
