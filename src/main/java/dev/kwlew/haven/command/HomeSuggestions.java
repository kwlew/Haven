package dev.kwlew.haven.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.kwlew.haven.home.HomeManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Suggests the sender's own home names.
 * <p>
 * Reads only the in-memory cache and returns empty for an uncached player - completion must never
 * block on disk.
 */
public class HomeSuggestions implements SuggestionProvider<CommandSourceStack> {

    private final HomeManager homeManager;

    public HomeSuggestions(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
                                                         SuggestionsBuilder builder) {
        if (context.getSource().getSender() instanceof Player player) {
            String typed = builder.getRemainingLowerCase();

            for (String name : homeManager.cachedNames(player)) {
                if (name.startsWith(typed)) {
                    builder.suggest(name);
                }
            }
        }

        return builder.buildFuture();
    }
}
