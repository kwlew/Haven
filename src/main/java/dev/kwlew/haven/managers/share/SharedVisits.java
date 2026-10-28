package dev.kwlew.haven.managers.share;

import dev.kwlew.haven.command.PlayerLookup;
import dev.kwlew.haven.managers.home.Home;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.PlayerHomes;
import dev.kwlew.haven.managers.home.WorldNames;
import dev.kwlew.haven.managers.home.WorldRules;
import dev.kwlew.haven.managers.message.Messages;
import dev.kwlew.haven.managers.sound.HavenSound;
import dev.kwlew.haven.managers.sound.Sounds;
import dev.kwlew.haven.managers.teleport.TeleportService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Teleport to a home someone else shared. The owner's data decides, never the visitor's hint, and a missing and an
 * unshared home give the same message so strangers can't probe home names.
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

    /** For callers that already know the owner's id (the menu), which survives a name change. */
    public void visit(Player player, UUID ownerId, String ownerName, String rawHome) {
        if (refuseIfDisabled(player)) {
            return;
        }

        String homeName = Home.normalize(rawHome);

        homeManager.load(ownerId).whenComplete((ownerHomes, error) -> {
            // The load may have taken a tick or two; also keeps homesOf from re-caching a quitter.
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
                // whenComplete would otherwise swallow this.
                plugin.getLogger().log(Level.SEVERE, "Shared-home visit failed for " + player.getName(), e);
            }
        });
    }

    private void arrive(Player player, UUID ownerId, String ownerName, String homeName,
                        PlayerHomes ownerHomes) {
        Home home = ownerHomes.get(homeName);
        boolean shared = home != null && home.isSharedWith(player.getUniqueId());

        // Make the visitor's hint agree with the owner's data: prune stale, add missing, refresh the owner name.
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
