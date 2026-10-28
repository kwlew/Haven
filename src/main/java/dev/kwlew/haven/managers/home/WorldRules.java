package dev.kwlew.haven.managers.home;

import dev.kwlew.haven.managers.config.HavenConfig;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.OptionalInt;

/** Per-world rules for setting and teleporting to homes; read live from config so reload applies. */
public class WorldRules {

    public static final String BYPASS = "haven.bypass.worlds";

    private final HavenConfig config;

    public WorldRules(HavenConfig config) {
        this.config = config;
    }

    public boolean sethomeAllowed(Player player, String world) {
        return !config.disabledWorlds().contains(world) || player.hasPermission(BYPASS);
    }

    /** Only refuses when {@code worlds.block-teleport} is on. */
    public boolean teleportAllowed(Player player, String world) {
        return !config.blockTeleportToDisabledWorlds()
                || sethomeAllowed(player, world);
    }

    public OptionalInt capFor(String world) {
        Integer cap = config.worldLimits().get(world);

        return cap == null ? OptionalInt.empty() : OptionalInt.of(cap);
    }

    /** Empty for anyone who bypasses the cap. */
    public OptionalInt capFor(Player player, String world) {
        if (player.hasPermission(BYPASS) || player.hasPermission(HomeLimits.UNLIMITED_NODE)) {
            return OptionalInt.empty();
        }

        return capFor(world);
    }

    /**
     * Overwriting a home in place never counts toward the cap, so players over a lowered cap can still adjust their
     * homes.
     */
    public static boolean exceedsCap(OptionalInt cap, Collection<Home> homes, String name, String targetWorld) {
        if (cap.isEmpty()) {
            return false;
        }

        for (Home home : homes) {
            if (home.name().equals(name) && home.world().equals(targetWorld)) {
                return false;
            }
        }

        return countIn(homes, name, targetWorld) >= cap.getAsInt();
    }

    public static int countIn(Collection<Home> homes, String excluding, String world) {
        int count = 0;

        for (Home home : homes) {
            if (!home.name().equals(excluding) && home.world().equals(world)) {
                count++;
            }
        }

        return count;
    }
}
