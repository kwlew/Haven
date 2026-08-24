package dev.kwlew.haven.home;

import dev.kwlew.haven.config.HavenConfig;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves how many homes a player may have from their {@code haven.homes.<n>} permissions.
 * Highest granted value wins, falling back to the configured default.
 */
public class HomeLimits {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    static final String LIMIT_PREFIX = "haven.homes.";
    static final String UNLIMITED_NODE = "haven.homes.unlimited";

    private final HavenConfig config;

    public HomeLimits(HavenConfig config) {
        this.config = config;
    }

    public int limitFor(Player player) {
        Map<String, Boolean> effective = new HashMap<>();

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            effective.put(info.getPermission(), info.getValue());
        }

        return resolve(effective, config.defaultHomeLimit());
    }

    /**
     * The whole decision, expressed over a plain permission map so it can be exercised directly.
     *
     * @param effective permission node to granted/denied, as reported by the permission plugin
     * @param fallback  limit to use when no {@code haven.homes.<n>} node is granted
     */
    public static int resolve(Map<String, Boolean> effective, int fallback) {
        if (Boolean.TRUE.equals(effective.get(UNLIMITED_NODE))) {
            return UNLIMITED;
        }

        int highest = -1;

        for (Map.Entry<String, Boolean> entry : effective.entrySet()) {
            // Negated nodes appear here too; counting them would restore a cap an admin
            // explicitly took away.
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
                // A typo'd node like haven.homes.foo must not throw out of a command handler.
            }
        }

        return highest >= 0 ? highest : fallback;
    }

    public boolean isUnlimited(int limit) {
        return limit == UNLIMITED;
    }

    /**
     * Renders a limit for display, so messages don't show {@link Integer#MAX_VALUE}.
     */
    public String describe(int limit) {
        return isUnlimited(limit) ? "∞" : Integer.toString(limit);
    }
}
