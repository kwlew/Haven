package dev.kwlew.haven.command;

import dev.kwlew.haven.kernel.Inject;
import dev.kwlew.haven.managers.config.HavenConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Pending "/sethome over an existing home" confirmations; main-thread only. Tracks which home, so confirming a
 * different one doesn't move the wrong home.
 */
public class OverwriteConfirmations {

    private final IntSupplier timeoutSeconds;
    private final Map<UUID, Pending> pending = new HashMap<>();

    @Inject
    public OverwriteConfirmations(HavenConfig config) {
        this(config::confirmTimeoutSeconds);
    }

    /** A supplier (not an int) so {@code /haven reload} applies; tests drive expiry directly. */
    OverwriteConfirmations(IntSupplier timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

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
