package dev.kwlew.haven.managers.home;

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
 * In-memory homes of online players. {@link #cache} is main-thread only; {@link #preload} runs off-thread
 * and touches only the concurrent {@link #pending}. Writes are write-through via {@link #persist}.
 */
public class HomeManager implements LifecycleComponent {

    private static final long PENDING_MAX_AGE_MILLIS = 120_000L;
    private static final long SWEEP_INTERVAL_TICKS = 20L * 60L;

    private final JavaPlugin plugin;
    private final HomeStorage storage;

    private final Map<UUID, PlayerHomes> cache = new HashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    // Shares one offline copy between concurrent requests for the same player.
    private final Map<UUID, CompletableFuture<PlayerHomes>> loading = new HashMap<>();

    // Bumped on every offline write; Pending records the value its read started at.
    private final AtomicLong writeEpoch = new AtomicLong();
    private final Map<UUID, OfflineWrite> offlineWrites = new ConcurrentHashMap<>();

    private BukkitTask sweeper;

    public HomeManager(JavaPlugin plugin, HomeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public void start() {
        // Already-connected players never fire AsyncPlayerPreLoginEvent.
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

        for (PlayerHomes homes : cache.values()) {
            storage.save(homes.snapshot());
        }

        cache.clear();
        pending.clear();
        loading.clear();
        offlineWrites.clear();
    }

    /** Runs off the main thread during pre-login; parks the result until the player joins. */
    public void preload(UUID owner, String name) {
        // Sampled before the read is queued; see persistAny.
        long readEpoch = writeEpoch.get();

        pending.put(owner, new Pending(storage.load(owner, name), System.currentTimeMillis(), readEpoch));
    }

    public void discardPending(UUID owner) {
        pending.remove(owner);
    }

    /** Falls back to a synchronous read when the login skipped pre-login. */
    public void install(Player player) {
        UUID owner = player.getUniqueId();
        Pending parked = pending.remove(owner);
        OfflineWrite offlineWrite = offlineWrites.remove(owner);

        // Parked copy predates an offline edit; re-read so the read queues behind that write.
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

    /** Never null; a cache miss falls back to a synchronous read. */
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

    /** Empty when not cached; never reads from disk, so safe for tab-completion. */
    public java.util.List<String> cachedNames(Player player) {
        return cachedNames(player.getUniqueId());
    }

    public java.util.List<String> cachedNames(UUID id) {
        PlayerHomes homes = cache.get(id);

        return homes == null ? java.util.List.of() : homes.names();
    }

    /** Null when not cached; never reads from disk. Treat as read-only. */
    public PlayerHomes cached(Player player) {
        return cache.get(player.getUniqueId());
    }

    /** Main thread only. */
    public int cachedHomeTotal() {
        int total = 0;

        for (PlayerHomes homes : cache.values()) {
            total += homes.count();
        }

        return total;
    }

    public void persist(PlayerHomes homes) {
        storage.save(homes.snapshot());
    }

    /**
     * Completes on the main thread. Online players yield the live instance; offline results are detached
     * copies, so apply the change and call {@link #persistAny} in the same continuation.
     */
    public CompletableFuture<PlayerHomes> load(UUID id) {
        PlayerHomes cached = cache.get(id);

        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Concurrent requests for one offline player must share a copy, or the second misses the first's write.
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
     * Main thread only. For an offline copy, also drops the parked pre-login result and stamps an epoch so a
     * join cannot install a copy lacking this write. Refused if the owner logged in since the copy was loaded.
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

        // Write first, then stamp: a read sampling the epoch after the stamp is queued after this write.
        storage.save(homes.snapshot());

        offlineWrites.put(owner, new OfflineWrite(writeEpoch.incrementAndGet(), System.currentTimeMillis()));
        pending.remove(owner);
    }

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
        // Stamps only matter to reads that started before them.
        offlineWrites.values().removeIf(write -> write.at() < cutoff);
    }

    private record Pending(PlayerHomes.Snapshot snapshot, long parkedAt, long readEpoch) {}

    private record OfflineWrite(long epoch, long at) {}
}
