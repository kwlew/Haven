package dev.kwlew.haven.command;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.kernel.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Tracks pending "/sethome on an existing home" confirmations.
 * <p>
 * The pending entry records <em>which</em> home is awaiting confirmation. Without that,
 * {@code /sethome base} followed by {@code /sethome spawn} would read as a confirmation and
 * silently move the wrong home.
 * <p>
 * Main-thread only; commands are the only caller.
 */
public class OverwriteConfirmations {

    private final IntSupplier timeoutSeconds;
    private final Map<UUID, Pending> pending = new HashMap<>();

    @Inject
    public OverwriteConfirmations(HavenConfig config) {
        this(config::confirmTimeoutSeconds);
    }

    /**
     * Takes the timeout as a supplier rather than a {@link HavenConfig} so the expiry logic can be
     * driven directly from tests. Still a supplier, not an int, so {@code /haven reload} applies.
     */
    OverwriteConfirmations(IntSupplier timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Returns true when this call confirms a matching pending request. Otherwise records a new
     * request and returns false, meaning the caller should prompt.
     */
    public boolean confirm(UUID player, String homeName) {
        return confirmAt(player, homeName, System.currentTimeMillis());
    }

    boolean confirmAt(UUID player, String homeName, long now) {
        Pending existing = pending.get(player);

        if (existing != null && existing.homeName().equals(homeName) && existing.expiresAt() > now) {
            pending.remove(player);
            return true;
        }

        pending.put(player, new Pending(homeName, now + timeoutSeconds.getAsInt() * 1000L));
        return false;
    }

    public void clear(UUID player) {
        pending.remove(player);
    }

    private record Pending(String homeName, long expiresAt) {}
}
