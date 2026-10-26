package dev.kwlew.haven.home;

import dev.kwlew.haven.kernel.LifecycleComponent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence for {@link PlayerHomes}.
 * <p>
 * The seam exists so command and teleport code never touches the storage format directly.
 * {@link dev.kwlew.haven.home.YamlHomeStorage} is the only implementation.
 */
public interface HomeStorage extends LifecycleComponent {

    /**
     * Reads a player's homes, blocking the calling thread. Returns an empty container when the
     * player has no file yet. Throws if an existing file cannot be loaded without losing data.
     * Safe to call off the main thread - and normally is, from pre-login.
     */
    PlayerHomes.Snapshot load(UUID owner, String lastKnownName);

    /**
     * Queues a read on the storage thread and returns immediately. Same result and failure modes as
     * {@link #load}, but the caller is never blocked - for callers (the main thread, serving an
     * admin command about an offline player) that must not wait on disk. The future completes on
     * the storage thread, so continuations that touch server state must hop back themselves.
     * <p>
     * Shares the FIFO queue with {@link #save}, so the read observes every write queued before it.
     */
    CompletableFuture<PlayerHomes.Snapshot> loadAsync(UUID owner, String lastKnownName);

    /**
     * Queues a write. Returns immediately; the snapshot is written on the storage thread.
     */
    void save(PlayerHomes.Snapshot snapshot);
}
