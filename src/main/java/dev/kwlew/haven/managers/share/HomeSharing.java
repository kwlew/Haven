package dev.kwlew.haven.managers.share;

import dev.kwlew.haven.managers.config.HavenConfig;
import dev.kwlew.haven.managers.home.Home;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.PlayerHomes;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * The owner's {@link Home#sharedWith} is the truth; each recipient's {@link PlayerHomes#received} is a hint that may
 * be stale. Visits ({@link SharedVisits}) {@link #reconcile fix} the hint, and hint updates here are best-effort.
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

    /** Empty when sharing is off, which hides the feature without discarding data. */
    public List<PlayerHomes.Received> visibleHints(PlayerHomes homes) {
        return enabled() ? homes.received() : List.of();
    }

    /** Uses {@code getOfflinePlayer(UUID)}, which never contacts Mojang; falls back to the id. */
    public static String nameOf(UUID player) {
        String name = Bukkit.getOfflinePlayer(player).getName();

        return name != null ? name : player.toString();
    }

    /** Returns whether {@code mine} changed and needs saving. */
    public static boolean reconcile(PlayerHomes mine, UUID owner, String ownerName, String home,
                                    boolean sharedWithMe) {
        return sharedWithMe
                ? mine.putReceived(new PlayerHomes.Received(owner, ownerName, home))
                : mine.removeReceived(owner, home);
    }

    public void addHint(UUID recipient, UUID owner, String ownerName, String home) {
        edit(recipient, hints -> reconcile(hints, owner, ownerName, home, true));
    }

    public void removeHint(UUID recipient, UUID owner, String home) {
        edit(recipient, hints -> reconcile(hints, owner, null, home, false));
    }

    public void dropHints(UUID owner, Home deleted) {
        for (UUID recipient : deleted.sharedWith()) {
            removeHint(recipient, owner, deleted.name());
        }
    }

    /** {@code before} is the pre-rename home, since it still lists the recipients. */
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
     * Edit and save run in one continuation, as {@link HomeManager#persistAny} requires; failure is logged and
     * dropped.
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
