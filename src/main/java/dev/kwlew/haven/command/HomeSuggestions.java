package dev.kwlew.haven.command;

import dev.kwlew.haven.managers.home.Home;
import dev.kwlew.haven.managers.home.HomeManager;
import dev.kwlew.haven.managers.home.PlayerHomes;
import dev.kwlew.haven.managers.share.HomeSharing;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/** Cache-only suggestions (never blocks on disk). Shared homes come from received hints, which may be stale. */
public class HomeSuggestions implements TabCompleter {

    private final HomeManager homeManager;
    private final HomeSharing sharing;
    private final PlayerLookup lookup;

    public HomeSuggestions(HomeManager homeManager, HomeSharing sharing, PlayerLookup lookup) {
        this.homeManager = homeManager;
        this.sharing = sharing;
        this.lookup = lookup;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1 || !sender.hasPermission(command.getPermission())) {
            return List.of();
        }

        String typed = args[0].toLowerCase(Locale.ROOT);
        return names(player, typed);
    }

    public List<String> names(Player player, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return homeManager.cachedNames(player).stream()
                .filter(name -> name.startsWith(prefix))
                .toList();
    }

    public List<String> visitable(Player player, String typed) {
        List<String> suggestions = new ArrayList<>(names(player, typed));
        PlayerHomes homes = homeManager.cached(player);

        if (homes == null) {
            return suggestions;
        }

        String prefix = typed.toLowerCase(Locale.ROOT);
        for (PlayerHomes.Received hint : sharing.visibleHints(homes)) {
            String candidate = hint.ownerName() + ":" + hint.home();

            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(candidate);
            }
        }

        return suggestions;
    }

    public List<String> sharedHomes(Player player, String typed) {
        PlayerHomes homes = homeManager.cached(player);

        if (homes == null) {
            return List.of();
        }

        String prefix = typed.toLowerCase(Locale.ROOT);
        return homes.all().stream()
                .filter(home -> !home.sharedWith().isEmpty())
                .map(Home::name)
                .filter(name -> name.startsWith(prefix))
                .sorted()
                .toList();
    }

    public List<String> otherPlayers(Player player, String typed) {
        return lookup.onlineNames(typed).stream()
                .filter(name -> !name.equals(player.getName()))
                .toList();
    }

    public List<String> recipients(Player player, String homeName, String typed) {
        PlayerHomes homes = homeManager.cached(player);
        Home home = homes == null ? null : homes.get(homeName);

        if (home == null) {
            return List.of();
        }

        String prefix = typed.toLowerCase(Locale.ROOT);
        Set<UUID> shared = home.sharedWith();
        return shared.stream()
                .map(HomeSharing::nameOf)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    TabCompleter forHome() {
        return forPlayer((player, args) -> args.length == 1 ? visitable(player, args[0]) : List.of());
    }

    TabCompleter forShare() {
        return forPlayer((player, args) -> switch (args.length) {
            case 1 -> names(player, args[0]);
            case 2 -> otherPlayers(player, args[1]);
            default -> List.of();
        });
    }

    TabCompleter forUnshare() {
        return forPlayer((player, args) -> switch (args.length) {
            case 1 -> sharedHomes(player, args[0]);
            case 2 -> recipients(player, args[0], args[1]);
            default -> List.of();
        });
    }

    private static TabCompleter forPlayer(BiFunction<Player, String[], List<String>> suggest) {
        return (sender, command, alias, args) ->
                sender instanceof Player player ? suggest.apply(player, args) : List.of();
    }
}
