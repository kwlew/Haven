package dev.kwlew.haven.share;

import dev.kwlew.haven.command.PlayerLookup;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.home.WorldNames;
import dev.kwlew.haven.home.WorldRules;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import dev.kwlew.haven.teleport.TeleportService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Teleporting to a home someone else shared: {@code /home owner:home}, and the same click in the
 * homes menu.
 * <p>
 * Lives apart from {@code HomeCommand} because the menu needs it too, and the menu is a dependency
 * of the command that lists homes.
 * <p>
 * The owner's data decides, never the visitor's hint. The owner's homes are loaded fresh (from
 * memory if they're online, otherwise from disk without blocking) and the visit proceeds only if
 * the home still exists and still lists the visitor. A missing home and an unshared one get the same
 * message, so a stranger can't use this to find out which home names exist.
 */
public class SharedVisits {

    private final JavaPlugin plugin;
    private final Messages messages;
    private final HomeManager homeManager;
    private final HomeSharing sharing;
    private final PlayerLookup lookup;
    private final WorldRules worldRules;
    private final TeleportService teleports;
    private final Sounds sounds;

    public SharedVisits(JavaPlugin plugin,
                        Messages messages,
                        HomeManager homeManager,
                        HomeSharing sharing,
                        PlayerLookup lookup,
                        WorldRules worldRules,
                        TeleportService teleports,
                        Sounds sounds) {
        this.plugin = plugin;
        this.messages = messages;
        this.homeManager = homeManager;
        this.sharing = sharing;
        this.lookup = lookup;
        this.worldRules = worldRules;
        this.teleports = teleports;
        this.sounds = sounds;
    }

    /** {@code /home <owner>:<home>}, where the owner is a typed name. */
    public void visit(Player player, String ownerName, String rawHome) {
        if (refuseIfDisabled(player)) {
            return;
        }

        PlayerLookup.Target owner = lookup.find(ownerName);

        if (owner == null) {
            notFound(player, ownerName, rawHome);
            return;
        }

        visit(player, owner.id(), owner.name(), rawHome);
    }

    /**
     * As above, for a caller that already knows who the owner is - the menu, whose entries come
     * from the player's hints and so carry an id that survives the owner changing their name.
     */
    public void visit(Player player, UUID ownerId, String ownerName, String rawHome) {
        if (refuseIfDisabled(player)) {
            return;
        }

        String homeName = Home.normalize(rawHome);

        homeManager.load(ownerId).whenComplete((ownerHomes, error) -> {
            // The load may have taken a tick or two. Also keeps homesOf from re-caching a quitter.
            if (!player.isOnline()) {
                return;
            }

            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not load homes for " + ownerId, error);
                messages.send(player, "teleport.failed");
                sounds.play(player, HavenSound.DENIED);
                return;
            }

            try {
                arrive(player, ownerId, ownerName, homeName, ownerHomes);
            } catch (RuntimeException e) {
                // whenComplete would otherwise swallow this into the discarded future.
                plugin.getLogger().log(Level.SEVERE, "Shared-home visit failed for " + player.getName(), e);
            }
        });
    }

    private void arrive(Player player, UUID ownerId, String ownerName, String homeName,
                        PlayerHomes ownerHomes) {
        Home home = ownerHomes.get(homeName);
        boolean shared = home != null && home.isSharedWith(player.getUniqueId());

        // Whatever the owner's data says, the visitor's own hint is made to agree with it: a stale
        // one is pruned, and a missing one (typed by hand) or an out-of-date owner name is fixed.
        PlayerHomes mine = homeManager.homesOf(player);
        String currentName = ownerHomes.lastKnownName() != null ? ownerHomes.lastKnownName() : ownerName;

        if (HomeSharing.reconcile(mine, ownerId, currentName, homeName, shared)) {
            homeManager.persist(mine);
        }

        if (!shared) {
            notFound(player, ownerName, homeName);
            return;
        }

        if (!worldRules.teleportAllowed(player, home.world())) {
            messages.send(player, "teleport.world-disabled",
                    Placeholder.unparsed("name", home.name()),
                    Placeholder.unparsed("world", WorldNames.displayName(messages, home.world())));
            sounds.play(player, HavenSound.DENIED);
            return;
        }

        // The trip is the visitor's: warmup, cooldown and the cooldown stamp all belong to them.
        teleports.teleport(player, home);
    }

    private boolean refuseIfDisabled(Player player) {
        if (sharing.enabled()) {
            return false;
        }

        messages.send(player, "share.disabled");
        sounds.play(player, HavenSound.DENIED);
        return true;
    }

    private void notFound(Player player, String ownerName, String home) {
        messages.send(player, "share.not-found",
                Placeholder.unparsed("owner", ownerName),
                Placeholder.unparsed("name", Home.normalize(home)));
        sounds.play(player, HavenSound.DENIED);
    }
}
