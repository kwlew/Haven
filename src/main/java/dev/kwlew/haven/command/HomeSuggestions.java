package dev.kwlew.haven.command;

import dev.kwlew.haven.home.HomeManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Suggests the sender's own home names.
 * <p>
 * Reads only the in-memory cache and returns empty for an uncached player - completion must never
 * block on disk.
 */
public class HomeSuggestions implements TabCompleter {

    private final HomeManager homeManager;

    public HomeSuggestions(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1 || !sender.hasPermission(command.getPermission())) {
            return List.of();
        }

        String typed = args[0].toLowerCase(Locale.ROOT);
        return homeManager.cachedNames(player).stream()
                .filter(name -> name.startsWith(typed))
                .toList();
    }
}
