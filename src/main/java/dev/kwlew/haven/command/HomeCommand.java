package dev.kwlew.haven.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import dev.kwlew.haven.teleport.TeleportService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public class HomeCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final TeleportService teleportService;
    private final HomeSuggestions suggestions;
    private final HomesCommand homesCommand;
    private final Sounds sounds;

    public HomeCommand(Messages messages,
                       HomeManager homeManager,
                       TeleportService teleportService,
                       HomeSuggestions suggestions,
                       HomesCommand homesCommand,
                       Sounds sounds) {
        super(messages);
        this.homeManager = homeManager;
        this.teleportService = teleportService;
        this.suggestions = suggestions;
        this.homesCommand = homesCommand;
        this.sounds = sounds;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("home")
                .requires(source -> source.getSender().hasPermission("haven.home"))
                .executes(context -> run(context, null))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(suggestions)
                        .executes(context -> run(context, StringArgumentType.getString(context, "name"))))
                .build();
    }

    private int run(CommandContext<CommandSourceStack> context, String rawName) {
        Player player = requirePlayer(context);
        if (player == null) {
            return 0;
        }

        PlayerHomes homes = homeManager.homesOf(player);

        if (homes.count() == 0) {
            messages.send(player, "home.list-empty");
            return 0;
        }

        Home target;

        if (rawName != null) {
            target = homes.get(rawName);

            if (target == null) {
                messages.send(player, "home.not-found",
                        Placeholder.unparsed("name", Home.normalize(rawName)));
                sounds.play(player, HavenSound.DENIED);
                return 0;
            }
        } else {
            target = defaultHome(homes);

            if (target == null) {
                // Ambiguous - show the list rather than guessing which home they meant.
                homesCommand.sendList(player);
                return 0;
            }
        }

        teleportService.teleport(player, target);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolves bare {@code /home}. A home literally named "home" wins before the only-one-home
     * shortcut, so behaviour doesn't change for a player the moment they add a second home.
     */
    private Home defaultHome(PlayerHomes homes) {
        Home named = homes.get(SetHomeCommand.DEFAULT_HOME);
        if (named != null) {
            return named;
        }

        return homes.count() == 1 ? homes.all().iterator().next() : null;
    }
}
