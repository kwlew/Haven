package dev.kwlew.haven.command;

import dev.kwlew.haven.kernel.Inject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Turns a typed player name into an id for admin commands that act on someone who may be offline.
 * <p>
 * Resolution never touches the network or the disk. {@code Bukkit.getOfflinePlayer(String)} would:
 * for an unknown name it asks Mojang's session server <em>on the main thread</em>, and it happily
 * invents a UUID for a name nobody has ever used, which would let an admin typo read (and, for a
 * delete, write) a phantom player's file. Paper's {@code getOfflinePlayerIfCached} answers only from
 * the server's own user cache, so a name resolves exactly when that player has joined this server.
 */
public class PlayerLookup {

    /** A resolved player: the id their homes are filed under, and their canonical spelling. */
    public record Target(UUID id, String name) {}

    private final Function<String, Player> online;
    private final Function<String, OfflinePlayer> cached;

    @Inject
    public PlayerLookup() {
        this(Bukkit::getPlayerExact, Bukkit::getOfflinePlayerIfCached);
    }

    PlayerLookup(Function<String, Player> online, Function<String, OfflinePlayer> cached) {
        this.online = online;
        this.cached = cached;
    }

    /**
     * An exact online match first, so a connected player is found even before the user cache
     * records them; then the user cache. {@code null} when the name is unknown.
     */
    public Target find(String name) {
        Player player = online.apply(name);

        if (player != null) {
            return new Target(player.getUniqueId(), player.getName());
        }

        OfflinePlayer known = cached.apply(name);

        // A cache entry always has a name; a null one is a half-built object we can't trust.
        if (known == null || known.getName() == null) {
            return null;
        }

        return new Target(known.getUniqueId(), known.getName());
    }

    /**
     * The connected player of that exact name, or {@code null}. Completion uses this to decide
     * whether the target's homes are in memory without ever falling back to a disk read.
     */
    public Player online(String name) {
        return online.apply(name);
    }

    /** Names of online players starting with {@code typed}, ignoring case. */
    public List<String> onlineNames(String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
