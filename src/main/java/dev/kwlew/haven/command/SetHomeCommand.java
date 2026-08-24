package dev.kwlew.haven.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.kwlew.haven.config.HavenConfig;
import dev.kwlew.haven.home.Home;
import dev.kwlew.haven.home.HomeLimits;
import dev.kwlew.haven.home.HomeManager;
import dev.kwlew.haven.home.PlayerHomes;
import dev.kwlew.haven.message.Messages;
import dev.kwlew.haven.sound.HavenSound;
import dev.kwlew.haven.sound.Sounds;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public class SetHomeCommand extends PlayerCommand {

    /** Name used when /sethome is run with no argument. */
    public static final String DEFAULT_HOME = "home";

    private final HavenConfig config;
    private final HomeManager homeManager;
    private final HomeLimits limits;
    private final OverwriteConfirmations confirmations;
    private final Sounds sounds;

    public SetHomeCommand(Messages messages,
                          HavenConfig config,
                          HomeManager homeManager,
                          HomeLimits limits,
                          OverwriteConfirmations confirmations,
                          Sounds sounds) {
        super(messages);
        this.config = config;
        this.homeManager = homeManager;
        this.limits = limits;
        this.confirmations = confirmations;
        this.sounds = sounds;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("sethome")
                .requires(source -> source.getSender().hasPermission("haven.sethome"))
                .executes(context -> run(context, DEFAULT_HOME))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> run(context, StringArgumentType.getString(context, "name"))))
                .build();
    }

    private int run(CommandContext<CommandSourceStack> context, String rawName) {
        Player player = requirePlayer(context);
        if (player == null) {
            return 0;
        }

        String name = Home.normalize(rawName);
        if (!Home.isValidName(name)) {
            messages.send(player, "home.invalid-name");
            sounds.play(player, HavenSound.DENIED);
            return 0;
        }

        PlayerHomes homes = homeManager.homesOf(player);
        int limit = limits.limitFor(player);
        boolean exists = homes.has(name);

        // Only new homes count against the limit, so a player over a lowered cap can still move
        // the homes they already have.
        if (!exists && homes.count() >= limit) {
            messages.send(player, "home.limit-reached",
                    Placeholder.unparsed("count", Integer.toString(homes.count())),
                    Placeholder.unparsed("max", limits.describe(limit)));
            sounds.play(player, HavenSound.DENIED);
            return 0;
        }

        if (exists && !confirmations.confirm(player.getUniqueId(), name)) {
            messages.send(player, "home.overwrite-confirm",
                    Placeholder.unparsed("name", name),
                    Placeholder.unparsed("seconds", Integer.toString(config.confirmTimeoutSeconds())));
            sounds.play(player, HavenSound.DENIED);
            return 0;
        }

        homes.put(Home.of(name, player.getLocation()));
        homeManager.persist(homes);
        sounds.play(player, HavenSound.HOME_SET);

        messages.send(player, exists ? "home.overwritten" : "home.set",
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("count", Integer.toString(homes.count())),
                Placeholder.unparsed("max", limits.describe(limit)));

        return Command.SINGLE_SUCCESS;
    }
}
