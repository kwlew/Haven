package dev.kwlew.haven.home;

import dev.kwlew.haven.kernel.LifecycleComponent;

import java.util.UUID;

/**
 * Persistence for {@link PlayerHomes}.
 * <p>
 * The seam exists so command and teleport code never touches the storage format directly.
 * {@link dev.kwlew.haven.home.YamlHomeStorage} is the only implementation.
 */
public interface HomeStorage extends LifecycleComponent {

    /**
     * Reads a player's homes, blocking the calling thread. Returns an empty container when the
     * player has no file yet. Safe to call off the main thread - and normally is, from pre-login.
     */
    PlayerHomes.Snapshot load(UUID owner, String lastKnownName);

    /**
     * Queues a write. Returns immediately; the snapshot is written on the storage thread.
     */
    void save(PlayerHomes.Snapshot snapshot);
}
