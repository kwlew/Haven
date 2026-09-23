package dev.kwlew.haven.api.papi;

import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Exposes Haven's per-player numbers to PlaceholderAPI.
 * <p>
 * Every placeholder reads the in-memory cache on the calling thread, so all of them require the
 * player to be <em>online</em>; offline lookups would mean disk I/O on whatever thread PAPI
 * happens to call from. Unknown or offline requests return an empty string, which is how PAPI
 * expects an expansion to decline.
 *
 * <ul>
 *   <li>{@code %haven_homes_used%} - homes currently saved</li>
 *   <li>{@code %haven_homes_max%} - their limit ("∞" when unlimited)</li>
 *   <li>{@code %haven_homes_free%} - remaining slots ("∞" when unlimited)</li>
 *   <li>{@code %haven_homes_list%} - comma-separated home names</li>
 * </ul>
 */
public class PlaceholderAPI extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final HomeManager homeManager;
    private final HomeLimits limits;

    public PlaceholderAPI(JavaPlugin plugin, HomeManager homeManager, HomeLimits limits) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.limits = limits;
    }

    @Override
    public @NotNull String getIdentifier() {
        // Lowercase: this is the %<identifier>_...% prefix, not a display name.
        return plugin.getDescription().getName().toLowerCase(Locale.ROOT);
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer requester, @NotNull String params) {
        if (requester == null || !requester.isOnline()) {
            return "";
        }

        Player player = requester.getPlayer();
        if (player == null) {
            return "";
        }

        int used = homeManager.homesOf(player).count();
        int limit = limits.limitFor(player);

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "homes_used" -> Integer.toString(used);
            case "homes_max" -> limits.describe(limit);
            case "homes_free" -> limits.isUnlimited(limit)
                    ? limits.describe(limit)
                    : Integer.toString(Math.max(0, limit - used));
            case "homes_list" -> homeManager.homesOf(player).all().stream()
                    .map(Home::name)
                    .collect(Collectors.joining(", "));
            default -> null;
        };
    }
}
