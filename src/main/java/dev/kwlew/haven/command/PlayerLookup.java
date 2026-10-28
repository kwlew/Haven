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
 * Resolves a typed name for admin commands. Never touches network or disk: {@code Bukkit.getOfflinePlayer(String)}
 * would ask Mojang on the main thread and invent UUIDs for unknown names, so only the server's user cache is used.
 */
public class PlayerLookup {

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

    public Target find(String name) {
        Player player = online.apply(name);

        if (player != null) {
            return new Target(player.getUniqueId(), player.getName());
        }

        OfflinePlayer known = cached.apply(name);

        // A null name is a half-built cache entry we can't trust.
        if (known == null || known.getName() == null) {
            return null;
        }

        return new Target(known.getUniqueId(), known.getName());
    }

    /** Lets completion check whether the target's homes are in memory without a disk read. */
    public Player online(String name) {
        return online.apply(name);
    }

    public List<String> onlineNames(String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);

        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}
