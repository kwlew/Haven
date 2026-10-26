package dev.kwlew.haven.home;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A saved location.
 * <p>
 * Deliberately free of Bukkit API state: the world is held as a <em>name</em>, not a
 * {@link World} or UUID. That keeps the on-disk YAML readable and makes instances safe to hand to
 * the storage thread, which must never touch the server API. The world is resolved lazily at
 * teleport time, so a world that is unloaded or renamed produces a clean error instead of a stale
 * reference.
 * <p>
 * Immutable, including the set of players it is shared with: sharing produces a modified copy that
 * the caller {@link PlayerHomes#put puts back}, so a {@code Home} already handed to the storage
 * thread or to a teleport warmup can never change underneath it. Home names match
 * {@code [a-z0-9_-]}, so they never contain the {@code :} that separates owner and home in
 * {@code /home owner:home}.
 */
public record Home(
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long created,
        Set<UUID> sharedWith
) {

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,16}");

    /**
     * Copies {@code sharedWith} into an unmodifiable set that keeps insertion order, so the file
     * lists recipients in the order they were added instead of reshuffling on every save.
     */
    public Home {
        sharedWith = sharedWith == null || sharedWith.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(sharedWith));
    }

    /** A home shared with nobody. */
    public Home(String name, String world, double x, double y, double z, float yaw, float pitch,
                long created) {
        this(name, world, x, y, z, yaw, pitch, created, Set.of());
    }

    public static Home of(String name, Location location) {
        return new Home(
                normalize(name),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                System.currentTimeMillis()
        );
    }

    public boolean isSharedWith(UUID player) {
        return sharedWith.contains(player);
    }

    /** This home, also visible to {@code player}. Returns {@code this} if they already see it. */
    public Home withSharedWith(UUID player) {
        if (sharedWith.contains(player)) {
            return this;
        }

        Set<UUID> grown = new LinkedHashSet<>(sharedWith);
        grown.add(player);

        return withShares(grown);
    }

    /** This home, no longer visible to {@code player}. Returns {@code this} if they never saw it. */
    public Home withoutSharedWith(UUID player) {
        if (!sharedWith.contains(player)) {
            return this;
        }

        Set<UUID> shrunk = new LinkedHashSet<>(sharedWith);
        shrunk.remove(player);

        return withShares(shrunk);
    }

    /**
     * The same home under another name, keeping its shares. Callers that rebuild a home field by
     * field are how a rename once risked dropping them, so go through here.
     */
    public Home renamed(String newName) {
        return new Home(newName, world, x, y, z, yaw, pitch, created, sharedWith);
    }

    /**
     * This home's name and shares at the position (and creation time) of {@code moved}. Overwriting
     * a home with {@code /sethome} must not silently unshare it.
     */
    public Home movedTo(Home moved) {
        return new Home(name, moved.world, moved.x, moved.y, moved.z,
                moved.yaw, moved.pitch, moved.created, sharedWith);
    }

    private Home withShares(Set<UUID> shares) {
        return new Home(name, world, x, y, z, yaw, pitch, created, shares);
    }

    /**
     * Lowercases with {@link Locale#ROOT}. A locale-sensitive {@code toLowerCase()} would map
     * {@code I} to {@code ı} under {@code tr_TR}, which both fails {@link #isValidName} and
     * desynchronises the write path from the lookup path.
     */
    public static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(normalize(name)).matches();
    }

    /**
     * Resolves this home against the running server, or {@code null} if its world is not loaded.
     * Never creates the world - that would silently generate a fresh one after a rename.
     */
    public Location toLocation(org.bukkit.Server server) {
        World resolved = server.getWorld(world);

        return resolved == null ? null : new Location(resolved, x, y, z, yaw, pitch);
    }
}
