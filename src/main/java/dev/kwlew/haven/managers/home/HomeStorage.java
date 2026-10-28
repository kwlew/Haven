package dev.kwlew.haven.managers.home;

import dev.kwlew.haven.kernel.LifecycleComponent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistence for {@link PlayerHomes}; {@link YamlHomeStorage} is the only implementation. */
public interface HomeStorage extends LifecycleComponent {

    /**
     * Blocks the calling thread (safe off the main thread, e.g. pre-login). Empty container if no file;
     * throws if an existing file cannot be loaded without losing data.
     */
    PlayerHomes.Snapshot load(UUID owner, String lastKnownName);

    /**
     * Non-blocking {@link #load}. Completes on the storage thread, so continuations touching server state must
     * hop back; shares the FIFO queue with {@link #save}, so it sees every earlier write.
     */
    CompletableFuture<PlayerHomes.Snapshot> loadAsync(UUID owner, String lastKnownName);

    void save(PlayerHomes.Snapshot snapshot);
}
