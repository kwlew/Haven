package dev.kwlew.haven.home;

import dev.kwlew.haven.config.HavenConfig;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.OptionalInt;

/**
 * Which worlds a player may set homes in, teleport into, and how many homes each world may hold.
 * <p>
 * Reads {@link HavenConfig} on every call so {@code /haven reload} applies. The per-world cap
 * decision is a static helper over plain data, so it can be tested without a server.
 */
public class WorldRules {

    public static final String BYPASS = "haven.bypass.worlds";

    private final HavenConfig config;

    public WorldRules(HavenConfig config) {
        this.config = config;
    }

    /** False when {@code world} is disabled and the player lacks the bypass. */
    public boolean sethomeAllowed(Player player, String world) {
        return !config.disabledWorlds().contains(world) || player.hasPermission(BYPASS);
    }

    /**
     * Only refuses when {@code worlds.block-teleport} is on; otherwise a disabled world still
     * accepts teleports to the homes players already have there.
     */
    public boolean teleportAllowed(Player player, String world) {
        return !config.blockTeleportToDisabledWorlds()
                || sethomeAllowed(player, world);
    }

    /** The configured cap for a world, ignoring who is asking. Empty means uncapped. */
    public OptionalInt capFor(String world) {
        Integer cap = config.worldLimits().get(world);

        return cap == null ? OptionalInt.empty() : OptionalInt.of(cap);
    }

    /**
     * The cap that applies to this player: empty for anyone who bypasses it, either with
     * {@code haven.bypass.worlds} or {@code haven.homes.unlimited}.
     */
    public OptionalInt capFor(Player player, String world) {
        if (player.hasPermission(BYPASS) || player.hasPermission(HomeLimits.UNLIMITED_NODE)) {
            return OptionalInt.empty();
        }

        return capFor(world);
    }

    /**
     * Whether saving the home called {@code name} into {@code targetWorld} would exceed the cap.
     * <p>
     * Only a new home, or an existing one moving in from another world, adds to the target world.
     * Overwriting a home in place never changes the count, so it is never blocked - otherwise a
     * player over a lowered cap could not even adjust the homes they already have. The home being
     * moved is excluded from the count for the same reason it is not yet in the target world.
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

    /** Homes in {@code world}, not counting the one called {@code excluding}. */
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
