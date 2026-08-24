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
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public class DelHomeCommand extends PlayerCommand {

    private final HomeManager homeManager;
    private final HomeSuggestions suggestions;
    private final OverwriteConfirmations confirmations;
    private final Sounds sounds;

    public DelHomeCommand(Messages messages,
                          HomeManager homeManager,
                          HomeSuggestions suggestions,
                          OverwriteConfirmations confirmations,
                          Sounds sounds) {
        super(messages);
        this.homeManager = homeManager;
        this.suggestions = suggestions;
        this.confirmations = confirmations;
        this.sounds = sounds;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("delhome")
                .requires(source -> source.getSender().hasPermission("haven.delhome"))
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

        String name = Home.normalize(rawName);
        PlayerHomes homes = homeManager.homesOf(player);
        Home removed = homes.remove(name);

        if (removed == null) {
            messages.send(player, "home.not-found", Placeholder.unparsed("name", name));
            sounds.play(player, HavenSound.DENIED);
            return 0;
        }

        // A pending overwrite for a home that no longer exists would be misleading.
        confirmations.clear(player.getUniqueId());
        homeManager.persist(homes);
        sounds.play(player, HavenSound.HOME_DELETED);

        // Echo the coordinates so an accidental deletion can be recreated from chat history.
        messages.send(player, "home.deleted",
                Placeholder.unparsed("name", removed.name()),
                Placeholder.unparsed("world", removed.world()),
                Placeholder.unparsed("x", Integer.toString((int) Math.floor(removed.x()))),
                Placeholder.unparsed("y", Integer.toString((int) Math.floor(removed.y()))),
                Placeholder.unparsed("z", Integer.toString((int) Math.floor(removed.z()))));

        return Command.SINGLE_SUCCESS;
    }
}
