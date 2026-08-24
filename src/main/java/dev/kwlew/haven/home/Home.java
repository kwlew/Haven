package dev.kwlew.haven.home;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A saved location.
 * <p>
 * Deliberately free of Bukkit API state: the world is held as a <em>name</em>, not a
 * {@link World} or UUID. That keeps the on-disk YAML readable and makes instances safe to hand to
 * the storage thread, which must never touch the server API. The world is resolved lazily at
 * teleport time, so a world that is unloaded or renamed produces a clean error instead of a stale
 * reference.
 */
public record Home(
        String name,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long created
) {

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,16}");

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
