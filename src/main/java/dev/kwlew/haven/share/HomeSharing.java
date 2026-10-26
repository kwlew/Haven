package dev.kwlew.haven.share;

import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Keeps the two halves of a share consistent.
 * <p>
 * A share lives in two places. The owner's {@link Home#sharedWith} is the truth and decides who may
 * visit. Each recipient's {@link PlayerHomes#received} is a hint copied from it so that
 * {@code /homes} and tab-completion can list shared homes without reading anyone else's file.
 * <p>
 * The hint is allowed to be wrong, because it can't be updated atomically with the owner's file: the
 * recipient may be offline, or their file may fail to load. So nothing trusts it. A visit re-reads
 * the owner's home ({@link SharedVisits}) and {@link #reconcile fixes} the hint to match whatever it
 * finds, which makes a stale hint harmless and self-correcting. The best-effort updates here exist
 * only to make the common case tidy, and a failure in one never fails the owner's command.
 */
public class HomeSharing {

    private final JavaPlugin plugin;
    private final HavenConfig config;
    private final HomeManager homeManager;

    public HomeSharing(JavaPlugin plugin, HavenConfig config, HomeManager homeManager) {
        this.plugin = plugin;
        this.config = config;
        this.homeManager = homeManager;
    }

    public boolean enabled() {
        return config.sharingEnabled();
    }

    public int maxPerHome() {
        return config.sharingMaxPerHome();
    }

    /**
     * The received-home hints worth showing: none at all when sharing is switched off, so turning
     * it off hides the feature without discarding anyone's data.
     */
    public List<PlayerHomes.Received> visibleHints(PlayerHomes homes) {
        return enabled() ? homes.received() : List.of();
    }

    /**
     * A player's name from the server's own cache. {@code getOfflinePlayer(UUID)} never contacts
     * Mojang, unlike the by-name overload, so it is safe on the main thread. Falls back to the id
     * for someone the server has never recorded.
     */
    public static String nameOf(UUID player) {
        String name = Bukkit.getOfflinePlayer(player).getName();

        return name != null ? name : player.toString();
    }

    /**
     * Makes {@code mine}'s hint about {@code owner}'s {@code home} agree with what the owner's data
     * said: present and current if the home really is shared with this player, absent otherwise.
     *
     * @return whether the hints changed, i.e. whether {@code mine} needs saving
     */
    public static boolean reconcile(PlayerHomes mine, UUID owner, String ownerName, String home,
                                    boolean sharedWithMe) {
        return sharedWithMe
                ? mine.putReceived(new PlayerHomes.Received(owner, ownerName, home))
                : mine.removeReceived(owner, home);
    }

    /** Adds a hint to a recipient who may be offline. */
    public void addHint(UUID recipient, UUID owner, String ownerName, String home) {
        edit(recipient, hints -> reconcile(hints, owner, ownerName, home, true));
    }

    /** Removes a recipient's hint. */
    public void removeHint(UUID recipient, UUID owner, String home) {
        edit(recipient, hints -> reconcile(hints, owner, null, home, false));
    }

    /**
     * Removes every recipient's hint for a home that was just deleted.
     */
    public void dropHints(UUID owner, Home deleted) {
        for (UUID recipient : deleted.sharedWith()) {
            removeHint(recipient, owner, deleted.name());
        }
    }

    /**
     * Points every recipient's hint at a home's new name. {@code before} is the home as it was
     * before the rename, since that is what still lists the recipients.
     */
    public void renameHints(UUID owner, String ownerName, Home before, String newName) {
        for (UUID recipient : before.sharedWith()) {
            edit(recipient, hints -> {
                boolean removed = reconcile(hints, owner, ownerName, before.name(), false);
                boolean added = reconcile(hints, owner, ownerName, newName, true);

                return removed || added;
            });
        }
    }

    /**
     * Applies {@code change} to a player's container and saves it if it reported a change. The
     * edit and the save happen in the same continuation, as {@link HomeManager#persistAny} requires
     * for an offline copy. Failure is logged and dropped: the hint is only a convenience.
     */
    private void edit(UUID player, Predicate<PlayerHomes> change) {
        homeManager.load(player).whenComplete((homes, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not update shared-home hints for " + player + "; they will correct themselves.", error);
                return;
            }

            try {
                if (change.test(homes)) {
                    homeManager.persistAny(homes);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not update shared-home hints for " + player, e);
            }
        });
    }
}
