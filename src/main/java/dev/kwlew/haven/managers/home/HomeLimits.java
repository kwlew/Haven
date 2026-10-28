package dev.kwlew.haven.managers.home;

import dev.kwlew.haven.managers.config.HavenConfig;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Highest granted {@code haven.homes.<n>} wins, else the configured default. */
public class HomeLimits {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    static final String LIMIT_PREFIX = "haven.homes.";
    static final String UNLIMITED_NODE = "haven.homes.unlimited";

    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final HavenConfig config;
    // Concurrent: PlaceholderAPI may query off the main thread.
    private final Map<UUID, CachedLimit> cache = new ConcurrentHashMap<>();

    public HomeLimits(HavenConfig config) {
        this.config = config;
    }

    public int limitFor(Player player) {
        long now = System.nanoTime();
        CachedLimit cached = cache.get(player.getUniqueId());

        if (cached != null && now - cached.computedAt < CACHE_TTL_NANOS) {
            return cached.value;
        }

        int value = compute(player);
        cache.put(player.getUniqueId(), new CachedLimit(value, now));

        return value;
    }

    public void invalidate(UUID player) {
        cache.remove(player);
    }

    public void clear() {
        cache.clear();
    }

    private int compute(Player player) {
        Map<String, Boolean> effective = new HashMap<>();

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            effective.put(info.getPermission(), info.getValue());
        }

        return resolve(effective, config.defaultHomeLimit());
    }

    private record CachedLimit(int value, long computedAt) {
    }

    /** Pure over a permission map so it can be tested directly. */
    public static int resolve(Map<String, Boolean> effective, int fallback) {
        if (Boolean.TRUE.equals(effective.get(UNLIMITED_NODE))) {
            return UNLIMITED;
        }

        int highest = -1;

        for (Map.Entry<String, Boolean> entry : effective.entrySet()) {
            // Negated nodes appear here too; counting them would restore a cap an admin removed.
            if (!Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }

            String node = entry.getKey();
            if (!node.startsWith(LIMIT_PREFIX)) {
                continue;
            }

            try {
                highest = Math.max(highest, Integer.parseInt(node.substring(LIMIT_PREFIX.length())));
            } catch (NumberFormatException ignored) {
                // A typo'd node must not throw out of a command handler.
            }
        }

        return highest >= 0 ? highest : fallback;
    }

    public boolean isUnlimited(int limit) {
        return limit == UNLIMITED;
    }

    public String describe(int limit) {
        return isUnlimited(limit) ? "∞" : Integer.toString(limit);
    }
}
