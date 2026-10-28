package dev.kwlew.haven.managers.home;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A saved location, immutable (including shares) so it is safe to hand to the storage thread. The world is held
 * by name and resolved at teleport time. Names match {@code [a-z0-9_-]}, so they never contain the {@code :} of
 * {@code owner:home}.
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

    public Home {
        sharedWith = sharedWith == null || sharedWith.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(sharedWith));
    }

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

    public Home withSharedWith(UUID player) {
        if (sharedWith.contains(player)) {
            return this;
        }

        Set<UUID> grown = new LinkedHashSet<>(sharedWith);
        grown.add(player);

        return withShares(grown);
    }

    public Home withoutSharedWith(UUID player) {
        if (!sharedWith.contains(player)) {
            return this;
        }

        Set<UUID> shrunk = new LinkedHashSet<>(sharedWith);
        shrunk.remove(player);

        return withShares(shrunk);
    }

    /** Goes through here so a rename never drops shares. */
    public Home renamed(String newName) {
        return new Home(newName, world, x, y, z, yaw, pitch, created, sharedWith);
    }

    /** Keeps this home's name and shares, so {@code /sethome} over a home does not unshare it. */
    public Home movedTo(Home moved) {
        return new Home(name, moved.world, moved.x, moved.y, moved.z,
                moved.yaw, moved.pitch, moved.created, sharedWith);
    }

    private Home withShares(Set<UUID> shares) {
        return new Home(name, world, x, y, z, yaw, pitch, created, shares);
    }

    /** Locale-insensitive: {@code toLowerCase()} would map {@code I} to {@code ı} under {@code tr_TR}. */
    public static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(normalize(name)).matches();
    }

    /** Null if the world is not loaded; never creates it. */
    public Location toLocation(org.bukkit.Server server) {
        World resolved = server.getWorld(world);

        return resolved == null ? null : new Location(resolved, x, y, z, yaw, pitch);
    }
}
